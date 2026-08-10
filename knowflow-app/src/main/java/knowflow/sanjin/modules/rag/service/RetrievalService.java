package knowflow.sanjin.modules.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import knowflow.sanjin.common.config.QdrantProperties;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeBaseItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeChunk;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingClient;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeBaseItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeChunkMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.rag.dto.RetrievalTrace;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Retrieval：对检索查询生成 Embedding → Qdrant owner + 多知识库 OR filter 检索 → MySQL 批量回查并二次校验。
 *
 * <p>Qdrant 命中后必须从 MySQL 校验 owner、当前内容版本、Item 状态与 KB 关系；校验失败的点逐条丢弃，不使整次检索失败。 幽灵 Point（旧版本、已删除
 * Item、关系已移除）不会进入 Prompt。低分候选同样被剔除。
 */
@Service
public class RetrievalService {

  private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

  private final RagProperties properties;
  private final CurrentOwnerProvider currentOwnerProvider;
  private final EmbeddingClient embeddingClient;
  private final QdrantClient qdrantClient;
  private final QdrantProperties qdrantProperties;
  private final KnowledgeChunkMapper chunkMapper;
  private final KnowledgeItemMapper itemMapper;
  private final KnowledgeBaseItemMapper kbItemMapper;

  public RetrievalService(
      RagProperties properties,
      CurrentOwnerProvider currentOwnerProvider,
      EmbeddingClient embeddingClient,
      QdrantClient qdrantClient,
      QdrantProperties qdrantProperties,
      KnowledgeChunkMapper chunkMapper,
      KnowledgeItemMapper itemMapper,
      KnowledgeBaseItemMapper kbItemMapper) {
    this.properties = properties;
    this.currentOwnerProvider = currentOwnerProvider;
    this.embeddingClient = embeddingClient;
    this.qdrantClient = qdrantClient;
    this.qdrantProperties = qdrantProperties;
    this.chunkMapper = chunkMapper;
    this.itemMapper = itemMapper;
    this.kbItemMapper = kbItemMapper;
  }

  /**
   * 执行一次检索并返回按 score 降序的注入来源。检索失败（Embedding/Qdrant 异常）抛 {@link
   * knowflow.sanjin.modules.knowledge.exception.RetryableIndexException}，由调用方降级为普通回答（DEGRADED）。
   *
   * @param query Router 输出的 retrievalQuery；为空时回退调用方传入的当前问题。
   * @param selectedKbIds Router 选中的 KnowledgeBase（已去重、0～3）。
   * @param fallbackQuestion current question。
   */
  public RetrievalResult retrieve(String query, List<Long> selectedKbIds, String fallbackQuestion) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    String effectiveQuery = query == null || query.isBlank() ? fallbackQuestion : query;
    RetrievalTrace trace = new RetrievalTrace();
    trace.setRetrievalQuery(effectiveQuery);
    trace.setSelectedKnowledgeBaseIds(List.copyOf(selectedKbIds));

    float[] vector = embedQuery(effectiveQuery, ownerId);

    Map<String, Object> filter =
        java.util.Map.of(
            "must",
            List.of(
                java.util.Map.of("key", "user_id", "match", java.util.Map.of("value", ownerId)),
                java.util.Map.of(
                    "key", "knowledge_base_ids", "match", java.util.Map.of("any", selectedKbIds))));

    List<QdrantClient.ScoredPoint> candidates =
        qdrantClient.search(
            qdrantProperties.getCollectionName(), vector, properties.getTopK(), filter);
    trace.setQdrantCandidates(candidates.size());

    List<RetrievedSource> injected = validateAndLoad(candidates, selectedKbIds, ownerId, trace);
    trace.setInjectedCount(injected.size());
    return new RetrievalResult(injected, trace);
  }

  private float[] embedQuery(String query, long ownerId) {
    try {
      List<float[]> vectors = embeddingClient.embed(List.of(query));
      if (vectors.isEmpty()) {
        throw new knowflow.sanjin.modules.knowledge.exception.RetryableIndexException(
            knowflow.sanjin.common.error.ErrorCode.EMBEDDING_UNAVAILABLE,
            "Query embedding returned no vectors",
            null);
      }
      return vectors.get(0);
    } catch (RuntimeException e) {
      if (e instanceof knowflow.sanjin.modules.knowledge.exception.RetryableIndexException) {
        throw e;
      }
      throw new knowflow.sanjin.modules.knowledge.exception.RetryableIndexException(
          knowflow.sanjin.common.error.ErrorCode.EMBEDDING_UNAVAILABLE,
          "Query embedding failed",
          e);
    }
  }

  /** Qdrant 命中 → MySQL 回查：逐条校验并把有效项组装为 RetrievedSource（按 score 降序）。 */
  private List<RetrievedSource> validateAndLoad(
      List<QdrantClient.ScoredPoint> candidates,
      List<Long> selectedKbIds,
      long ownerId,
      RetrievalTrace trace) {
    if (candidates.isEmpty()) {
      return List.of();
    }

    // 低于阈值直接剔除
    List<QdrantClient.ScoredPoint> aboveThreshold = new ArrayList<>();
    for (QdrantClient.ScoredPoint c : candidates) {
      if (c.score() >= properties.getScoreThreshold()) {
        aboveThreshold.add(c);
      }
    }
    int below = candidates.size() - aboveThreshold.size();
    trace.setDiscardedByValidation(trace.getDiscardedByValidation() + below);

    // 批量回查 chunk（owner 过滤）
    List<String> chunkIds =
        aboveThreshold.stream()
            .map(c -> c.payload().path("chunk_id").asText(""))
            .filter(s -> !s.isBlank())
            .toList();
    if (chunkIds.isEmpty()) {
      return List.of();
    }
    List<KnowledgeChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .in(KnowledgeChunk::getChunkId, chunkIds)
                .eq(KnowledgeChunk::getOwnerId, ownerId));
    Map<String, KnowledgeChunk> chunkById = new LinkedHashMap<>();
    for (KnowledgeChunk c : chunks) {
      chunkById.putIfAbsent(c.getChunkId(), c);
    }

    // 批量加载 Item 与活跃关系
    List<Long> itemIds =
        chunks.stream().map(KnowledgeChunk::getKnowledgeItemId).distinct().toList();
    Map<Long, KnowledgeItem> itemById = loadItems(itemIds);
    Map<Long, Set<Long>> kbIdsByItem = loadActiveKbIds(itemIds, ownerId);

    Set<String> seenChunks = new HashSet<>();
    List<RetrievedSource> result = new ArrayList<>();
    for (QdrantClient.ScoredPoint c : aboveThreshold) {
      KnowledgeChunk chunk = chunkById.get(c.payload().path("chunk_id").asText(""));
      if (chunk == null) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }
      KnowledgeItem item = itemById.get(chunk.getKnowledgeItemId());
      if (item == null || !KnowledgeConstants.STATUS_ACTIVE.equals(item.getStatus())) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }
      // 当前内容版本校验：仅注入 indexed_version 对应的 chunk
      if (item.getIndexedVersion() == null
          || !item.getIndexedVersion().equals(chunk.getContentVersion())) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }
      // KB 关系校验：Item 的活跃 KB 必须与 Router 选中集合有交集
      Set<Long> activeKbs = kbIdsByItem.getOrDefault(item.getId(), Set.of());
      boolean overlapsSelected = false;
      for (Long kbId : selectedKbIds) {
        if (activeKbs.contains(kbId)) {
          overlapsSelected = true;
          break;
        }
      }
      if (!overlapsSelected) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }
      // 去重（同 chunk 重复命中）
      if (!seenChunks.add(chunk.getChunkId())) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }

      RetrievedSource source = new RetrievedSource();
      source.setSourceId(chunk.getChunkId());
      source.setItemId(item.getId().toString());
      source.setItemTitle(item.getTitle());
      source.setSourceType(item.getSourceType());
      source.setContentVersion(chunk.getContentVersion());
      source.setChunkIndex(chunk.getChunkIndex());
      source.setSnippet(trim(chunk.getContent()));
      source.setScore(c.score());
      result.add(source);
    }
    // 按 score 降序确定注入顺序，保证 [Sx] 编号稳定
    result.sort(java.util.Comparator.comparingDouble(RetrievedSource::getScore).reversed());
    return result;
  }

  private Map<Long, KnowledgeItem> loadItems(List<Long> itemIds) {
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, KnowledgeItem> map = new LinkedHashMap<>();
    itemMapper.selectBatchIds(itemIds).forEach(i -> map.putIfAbsent(i.getId(), i));
    return map;
  }

  private Map<Long, Set<Long>> loadActiveKbIds(List<Long> itemIds, long ownerId) {
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, Set<Long>> map = new LinkedHashMap<>();
    kbItemMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeBaseItem>()
                .in(KnowledgeBaseItem::getKnowledgeItemId, itemIds)
                .eq(KnowledgeBaseItem::getOwnerId, ownerId)
                .eq(KnowledgeBaseItem::getDeleted, false))
        .forEach(
            rel ->
                map.computeIfAbsent(rel.getKnowledgeItemId(), k -> new HashSet<>())
                    .add(rel.getKnowledgeBaseId()));
    return map;
  }

  private String trim(String content) {
    if (content == null) {
      return "";
    }
    int limit = Math.max(1, properties.getSnippetCharLimit());
    String s = content.replaceAll("\\s+", " ").trim();
    return s.length() > limit ? s.substring(0, limit) + "…" : s;
  }

  /** 检索结果：注入来源（按 score 降序）+ 诊断。 */
  public record RetrievalResult(List<RetrievedSource> sources, RetrievalTrace trace) {

    public boolean hasContext() {
      return sources != null && !sources.isEmpty();
    }
  }
}
