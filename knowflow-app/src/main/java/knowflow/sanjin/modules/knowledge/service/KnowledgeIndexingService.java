package knowflow.sanjin.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import knowflow.sanjin.common.config.EmbeddingProperties;
import knowflow.sanjin.common.config.QdrantProperties;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeBaseItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeChunk;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItemTag;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import knowflow.sanjin.modules.knowledge.exception.TerminalIndexException;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingClient;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeBaseItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeChunkMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemTagMapper;
import knowflow.sanjin.modules.knowledge.mapper.TagMapper;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.IndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 知识索引执行器：Chunk → 保存 MySQL → Embedding → Qdrant Upsert。
 *
 * <p>对 FULL 任务：重算 Chunk 与向量，成功后更新 knowledge_item.indexed_version 与 index_status， 并按 payload filter
 * 清理旧版本 Point。对 PAYLOAD 任务：仅按当前 relations/tags 更新已有 Point 的 metadata，不重算向量、不切换版本。Qdrant Payload
 * 不含完整正文。
 */
@Service
public class KnowledgeIndexingService implements IndexingService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingService.class);

  private final KnowledgeItemMapper itemMapper;
  private final KnowledgeChunkMapper chunkMapper;
  private final KnowledgeBaseItemMapper kbItemMapper;
  private final KnowledgeItemTagMapper itemTagMapper;
  private final TagMapper tagMapper;
  private final TextChunker textChunker;
  private final EmbeddingClient embeddingClient;
  private final QdrantClient qdrantClient;
  private final EmbeddingProperties embeddingProperties;
  private final QdrantProperties qdrantProperties;
  private final ObjectMapper objectMapper;

  public KnowledgeIndexingService(
      KnowledgeItemMapper itemMapper,
      KnowledgeChunkMapper chunkMapper,
      KnowledgeBaseItemMapper kbItemMapper,
      KnowledgeItemTagMapper itemTagMapper,
      TagMapper tagMapper,
      TextChunker textChunker,
      EmbeddingClient embeddingClient,
      QdrantClient qdrantClient,
      EmbeddingProperties embeddingProperties,
      QdrantProperties qdrantProperties) {
    this.itemMapper = itemMapper;
    this.chunkMapper = chunkMapper;
    this.kbItemMapper = kbItemMapper;
    this.itemTagMapper = itemTagMapper;
    this.tagMapper = tagMapper;
    this.textChunker = textChunker;
    this.embeddingClient = embeddingClient;
    this.qdrantClient = qdrantClient;
    this.embeddingProperties = embeddingProperties;
    this.qdrantProperties = qdrantProperties;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void execute(ProcessingTask task) {
    String businessKey = task.getBusinessKey();
    boolean payloadOnly = businessKey.endsWith(KnowledgeConstants.BUSINESS_KEY_PAYLOAD_SUFFIX);
    boolean deleteTask = ProcessingConstants.TASK_TYPE_KNOWLEDGE_DELETE.equals(task.getTaskType());
    long itemId = task.getBusinessId();
    log.info(
        "Indexing task {} item {} key {} payloadOnly={} delete={}",
        task.getId(),
        itemId,
        businessKey,
        payloadOnly,
        deleteTask);

    if (deleteTask) {
      deleteAllPoints(itemId);
      return;
    }

    KnowledgeItem item = itemMapper.selectById(itemId);
    if (item == null || !KnowledgeConstants.STATUS_ACTIVE.equals(item.getStatus())) {
      throw new TerminalIndexException(
          ErrorCode.INDEX_SCHEMA_FAILURE, "KnowledgeItem " + itemId + " no longer active");
    }

    String collection = qdrantProperties.getCollectionName();
    log.info("Ensuring Qdrant collection {}", collection);
    qdrantClient.ensureCollection(collection, embeddingProperties.getDimensions());

    if (payloadOnly) {
      updatePayloadOnly(item);
      return;
    }

    int version = taskContentVersion(businessKey, item);
    log.info("Indexing item {} contentVersion {}", itemId, version);
    indexVersion(item, version, collection);
  }

  /** 删除 Item 的全部 Qdrant Point（按 itemId payload filter，幂等）。 */
  private void deleteAllPoints(long itemId) {
    java.util.Map<String, Object> filter =
        java.util.Map.of(
            "must",
            List.of(
                java.util.Map.of(
                    "key", "knowledge_item_id", "match", java.util.Map.of("value", itemId))));
    qdrantClient.deletePointsByFilter(qdrantProperties.getCollectionName(), filter);
  }

  private void updatePayloadOnly(KnowledgeItem item) {
    // 仅更新现有 Point 的 payload；若无已索引版本则跳过（FULL 任务会覆盖）
    Integer indexedVersion = item.getIndexedVersion();
    if (indexedVersion == null) {
      return;
    }
    List<KnowledgeChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeItemId, item.getId())
                .eq(KnowledgeChunk::getContentVersion, indexedVersion));
    if (chunks.isEmpty()) {
      return;
    }
    List<String> pointIds =
        chunks.stream().map(c -> pointId(item, indexedVersion, c.getChunkIndex())).toList();
    ObjectNode payload = payload(item, indexedVersion, 0);
    qdrantClient.setPayload(qdrantProperties.getCollectionName(), pointIds, payload);
  }

  private void indexVersion(KnowledgeItem item, int version, String collection) {
    List<TextChunker.Chunk> chunks = textChunker.chunk(item.getTitle(), item.getContent());
    if (chunks.isEmpty()) {
      throw new TerminalIndexException(
          ErrorCode.CHUNK_EMPTY, "No chunks produced for KnowledgeItem " + item.getId());
    }

    // 幂等：重试或重复投递时先清掉该版本已有 chunk，再重新落库，避免唯一约束冲突
    chunkMapper.delete(
        new LambdaQueryWrapper<KnowledgeChunk>()
            .eq(KnowledgeChunk::getKnowledgeItemId, item.getId())
            .eq(KnowledgeChunk::getContentVersion, version));

    // 保存 Chunk 到 MySQL（事实源）
    List<String> embeddingTexts = new ArrayList<>();
    List<KnowledgeChunk> savedChunks = new ArrayList<>();
    int index = 0;
    for (TextChunker.Chunk chunk : chunks) {
      KnowledgeChunk kc = new KnowledgeChunk();
      kc.setKnowledgeItemId(item.getId());
      kc.setOwnerId(item.getOwnerId());
      kc.setContentVersion(version);
      kc.setChunkIndex(index);
      kc.setChunkId(chunkId(item, version, index));
      kc.setContent(chunk.text());
      kc.setHeadingPath(chunk.headingPath());
      chunkMapper.insert(kc);
      savedChunks.add(kc);
      // Embedding 输入：title + heading path + chunk body
      embeddingTexts.add(
          (chunk.headingPath() == null || chunk.headingPath().isBlank())
              ? chunk.text()
              : chunk.headingPath() + "\n" + chunk.text());
      index++;
    }
    log.info(
        "Saved {} chunks to MySQL, embedding {} texts", savedChunks.size(), embeddingTexts.size());

    // 批量 Embedding
    List<float[]> vectors = embeddingClient.embed(embeddingTexts);
    log.info("Embedded {} vectors", vectors.size());

    // Qdrant Upsert
    List<QdrantClient.Point> points = new ArrayList<>();
    for (int i = 0; i < savedChunks.size(); i++) {
      KnowledgeChunk kc = savedChunks.get(i);
      points.add(
          new QdrantClient.Point(
              pointId(item, version, kc.getChunkIndex()),
              vectors.get(i),
              payload(item, version, kc.getChunkIndex())));
    }
    qdrantClient.upsertPoints(collection, points);
    log.info("Upserted {} points to Qdrant", points.size());

    // 成功后切换当前索引版本
    itemMapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeItem>()
            .eq(KnowledgeItem::getId, item.getId())
            .eq(KnowledgeItem::getContentVersion, version)
            .set(KnowledgeItem::getIndexedVersion, version)
            .set(KnowledgeItem::getIndexStatus, KnowledgeConstants.INDEX_INDEXED)
            .set(KnowledgeItem::getIndexErrorCode, null)
            .set(KnowledgeItem::getIndexErrorMessage, null));

    // 清理旧版本 Point（content_version < 当前）
    java.util.Map<String, Object> oldFilter =
        java.util.Map.of(
            "must",
            List.of(
                java.util.Map.of(
                    "key", "knowledge_item_id", "match", java.util.Map.of("value", item.getId())),
                java.util.Map.of(
                    "key", "content_version", "range", java.util.Map.of("lt", version))));
    qdrantClient.deletePointsByFilter(collection, oldFilter);
  }

  private int taskContentVersion(String businessKey, KnowledgeItem item) {
    // 格式：KNOWLEDGE_ITEM:{itemId}:{contentVersion} 或
    // KNOWLEDGE_ITEM:{itemId}:{contentVersion}:PAYLOAD
    String key =
        businessKey.endsWith(KnowledgeConstants.BUSINESS_KEY_PAYLOAD_SUFFIX)
            ? businessKey.substring(
                0, businessKey.length() - KnowledgeConstants.BUSINESS_KEY_PAYLOAD_SUFFIX.length())
            : businessKey;
    String[] parts = key.split(KnowledgeConstants.BUSINESS_KEY_DELIMITER);
    int version;
    try {
      version = Integer.parseInt(parts[parts.length - 1]);
    } catch (RuntimeException e) {
      throw new RetryableIndexException(
          ErrorCode.INDEX_SCHEMA_FAILURE, "Unparseable business key " + businessKey);
    }
    if (version > item.getContentVersion()) {
      throw new RetryableIndexException(
          ErrorCode.INDEX_SCHEMA_FAILURE,
          "Task content version " + version + " ahead of item version " + item.getContentVersion());
    }
    return version;
  }

  /** 确定性 Chunk ID：ownerId:itemId:contentVersion:chunkIndex 的 UUID v5。 */
  private String chunkId(KnowledgeItem item, int version, int index) {
    return uuid5("chunk:" + item.getId() + ":" + version + ":" + index);
  }

  /** 确定性 Qdrant Point ID。 */
  private String pointId(KnowledgeItem item, int version, int index) {
    return uuid5("point:" + item.getId() + ":" + version + ":" + index);
  }

  private static String uuid5(String namespaceValue) {
    UUID namespace = UUID.nameUUIDFromBytes("knowflow".getBytes(StandardCharsets.UTF_8));
    byte[] name = namespaceValue.getBytes(StandardCharsets.UTF_8);
    UUID result = UUID.nameUUIDFromBytes(concat(namespace, name));
    return result.toString();
  }

  private static byte[] concat(UUID namespace, byte[] name) {
    byte[] a = toBytes(namespace);
    byte[] out = new byte[a.length + name.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(name, 0, out, a.length, name.length);
    return out;
  }

  private static byte[] toBytes(UUID uuid) {
    long msb = uuid.getMostSignificantBits();
    long lsb = uuid.getLeastSignificantBits();
    byte[] bytes = new byte[16];
    for (int i = 0; i < 8; i++) {
      bytes[i] = (byte) (msb >>> 8 * (7 - i));
      bytes[8 + i] = (byte) (lsb >>> 8 * (7 - i));
    }
    return bytes;
  }

  /** Qdrant Payload：不含完整正文，仅检索所需 metadata 与归属/标签。 */
  private ObjectNode payload(KnowledgeItem item, int version, int chunkIndex) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("user_id", item.getOwnerId());
    payload.put("knowledge_item_id", item.getId());
    payload.put("content_version", version);
    payload.put("chunk_index", chunkIndex);
    payload.put("chunk_id", chunkId(item, version, chunkIndex));
    payload.put("source_type", item.getSourceType());

    ArrayNode kbIds = payload.putArray("knowledge_base_ids");
    kbItemMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeBaseItem>()
                .eq(KnowledgeBaseItem::getKnowledgeItemId, item.getId())
                .eq(KnowledgeBaseItem::getDeleted, false))
        .forEach(rel -> kbIds.add(rel.getKnowledgeBaseId()));

    ArrayNode tags = payload.putArray("tags");
    itemTagMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeItemTag>()
                .eq(KnowledgeItemTag::getKnowledgeItemId, item.getId())
                .eq(KnowledgeItemTag::getDeleted, false))
        .forEach(
            rel -> {
              Tag tag = tagMapper.selectById(rel.getTagId());
              if (tag != null) {
                tags.add(tag.getNormalizedName());
              }
            });
    return payload;
  }
}
