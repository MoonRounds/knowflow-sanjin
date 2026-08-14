package knowflow.sanjin.modules.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import knowflow.sanjin.common.config.QdrantProperties;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocumentChunk;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingClient;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentChunkMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.rag.dto.RetrievalTrace;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Retrieval：对检索查询生成 Embedding → Qdrant owner + 单归属 KB OR filter 检索 → MySQL 批量回查并二次校验。
 *
 * <p>Qdrant 命中后必须从 MySQL 校验 owner、当前内容版本、Document 未软删、单归属 KB 命中 Router 选中集合且 KB 仍启用（deleted=false
 * && enabled=true）；校验失败的点逐条丢弃，不使整次检索失败。 幽灵 Point（旧版本、已删除 Document、KB 已禁用/删除）不会进入 Prompt。低分候选同样被剔除。
 */
@Service
public class RetrievalService {

  private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

  private final RagProperties properties;
  private final CurrentOwnerProvider currentOwnerProvider;
  private final EmbeddingClient embeddingClient;
  private final QdrantClient qdrantClient;
  private final QdrantProperties qdrantProperties;
  private final KnowledgeDocumentChunkMapper chunkMapper;
  private final KnowledgeDocumentMapper documentMapper;
  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final knowflow.sanjin.modules.embeddingconfig.service.EmbeddingConfigService
      embeddingConfigService;

  public RetrievalService(
      RagProperties properties,
      CurrentOwnerProvider currentOwnerProvider,
      EmbeddingClient embeddingClient,
      QdrantClient qdrantClient,
      QdrantProperties qdrantProperties,
      KnowledgeDocumentChunkMapper chunkMapper,
      KnowledgeDocumentMapper documentMapper,
      KnowledgeBaseMapper knowledgeBaseMapper,
      knowflow.sanjin.modules.embeddingconfig.service.EmbeddingConfigService
          embeddingConfigService) {
    this.properties = properties;
    this.currentOwnerProvider = currentOwnerProvider;
    this.embeddingClient = embeddingClient;
    this.qdrantClient = qdrantClient;
    this.qdrantProperties = qdrantProperties;
    this.chunkMapper = chunkMapper;
    this.documentMapper = documentMapper;
    this.knowledgeBaseMapper = knowledgeBaseMapper;
    this.embeddingConfigService = embeddingConfigService;
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

    Map<String, Object> filter = buildFilter(ownerId, selectedKbIds);

    List<QdrantClient.ScoredPoint> candidates =
        qdrantClient.search(
            qdrantProperties.getCollectionName(), vector, properties.getTopK(), filter);
    trace.setQdrantCandidates(candidates.size());

    List<RetrievedSource> injected = validateAndLoad(candidates, selectedKbIds, ownerId, trace);
    trace.setInjectedCount(injected.size());
    return new RetrievalResult(injected, trace);
  }

  /** owner must + 单归属 KB should-OR；selectedKbIds 为空时不发送空 should（仅 must）。 */
  private Map<String, Object> buildFilter(long ownerId, List<Long> selectedKbIds) {
    List<Map<String, Object>> must = new ArrayList<>();
    must.add(java.util.Map.of("key", "user_id", "match", java.util.Map.of("value", ownerId)));
    if (selectedKbIds.isEmpty()) {
      return java.util.Map.of("must", must);
    }
    List<Map<String, Object>> should = new ArrayList<>();
    for (Long kbId : selectedKbIds) {
      should.add(
          java.util.Map.of("key", "knowledge_base_id", "match", java.util.Map.of("value", kbId)));
    }
    return java.util.Map.of("must", must, "should", should);
  }

  private float[] embedQuery(String query, long ownerId) {
    try {
      List<float[]> vectors =
          embeddingClient.embed(List.of(query), embeddingConfigService.getCurrentSnapshot());
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
    List<KnowledgeDocumentChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocumentChunk>()
                .in(KnowledgeDocumentChunk::getChunkId, chunkIds)
                .eq(KnowledgeDocumentChunk::getOwnerId, ownerId));
    Map<String, KnowledgeDocumentChunk> chunkById = new LinkedHashMap<>();
    for (KnowledgeDocumentChunk c : chunks) {
      chunkById.putIfAbsent(c.getChunkId(), c);
    }

    // 批量加载 Document 与单归属 KB 状态（deleted=false && enabled=true 才可注入）
    List<Long> documentIds =
        chunks.stream().map(KnowledgeDocumentChunk::getKnowledgeDocumentId).distinct().toList();
    Map<Long, KnowledgeDocument> documentById = loadDocuments(documentIds);
    Map<Long, KnowledgeBase> enabledKbById = loadEnabledKbs(ownerId, documentById);

    Set<String> seenChunks = new HashSet<>();
    List<RetrievedSource> result = new ArrayList<>();
    for (QdrantClient.ScoredPoint c : aboveThreshold) {
      KnowledgeDocumentChunk chunk = chunkById.get(c.payload().path("chunk_id").asText(""));
      if (chunk == null) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }
      KnowledgeDocument document = documentById.get(chunk.getKnowledgeDocumentId());
      if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }
      // 当前内容版本校验：仅注入 indexed_version 对应的 chunk
      if (document.getIndexedVersion() == null
          || !document.getIndexedVersion().equals(chunk.getContentVersion())) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }
      // 单归属 KB 校验：Document 的 kbId 必须命中 Router 选中集合
      if (document.getKbId() == null || !selectedKbIds.contains(document.getKbId())) {
        trace.setDiscardedByValidation(trace.getDiscardedByValidation() + 1);
        continue;
      }
      // KB 仍启用（G10）：已删除/禁用的 KB 下文档不得注入
      KnowledgeBase kb = enabledKbById.get(document.getKbId());
      if (kb == null) {
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
      source.setDocumentId(document.getId().toString());
      source.setDocumentTitle(document.getTitle());
      source.setSourceType(document.getSourceType());
      source.setKnowledgeBaseId(kb.getId().toString());
      source.setKnowledgeBaseName(kb.getDisplayName());
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

  private Map<Long, KnowledgeDocument> loadDocuments(List<Long> documentIds) {
    if (documentIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, KnowledgeDocument> map = new LinkedHashMap<>();
    documentMapper.selectBatchIds(documentIds).forEach(d -> map.putIfAbsent(d.getId(), d));
    return map;
  }

  /** 加载文档归属且 owner 过滤、未软删、已启用的 KB；返回 map（不含删除/禁用 KB）。 */
  private Map<Long, KnowledgeBase> loadEnabledKbs(
      long ownerId, Map<Long, KnowledgeDocument> documentById) {
    List<Long> kbIds =
        documentById.values().stream()
            .map(KnowledgeDocument::getKbId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    if (kbIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, KnowledgeBase> map = new LinkedHashMap<>();
    knowledgeBaseMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeBase>()
                .in(KnowledgeBase::getId, kbIds)
                .eq(KnowledgeBase::getOwnerId, ownerId)
                .eq(KnowledgeBase::getDeleted, false)
                .eq(KnowledgeBase::getEnabled, true))
        .forEach(kb -> map.putIfAbsent(kb.getId(), kb));
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
