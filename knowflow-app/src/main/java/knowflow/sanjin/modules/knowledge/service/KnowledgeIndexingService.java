package knowflow.sanjin.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import knowflow.sanjin.common.config.QdrantProperties;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocumentChunk;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocumentTag;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.exception.RetryableIndexException;
import knowflow.sanjin.modules.knowledge.exception.TerminalIndexException;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingClient;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentChunkMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentTagMapper;
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
 * <p>对 FULL 任务：重算 Chunk 与向量，成功后更新 knowledge_document.indexed_version 与 index_status， 并按 payload
 * filter 清理旧版本 Point。对 PAYLOAD 任务：仅按当前归属/tags 更新已有 Point 的 metadata，不重算向量、不切换版本。Qdrant Payload
 * 不含完整正文。
 */
@Service
public class KnowledgeIndexingService implements IndexingService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingService.class);

  private final KnowledgeDocumentMapper documentMapper;
  private final KnowledgeDocumentChunkMapper chunkMapper;
  private final KnowledgeDocumentTagMapper documentTagMapper;
  private final TagMapper tagMapper;
  private final TextChunker textChunker;
  private final EmbeddingClient embeddingClient;
  private final QdrantClient qdrantClient;
  private final knowflow.sanjin.modules.embeddingconfig.service.EmbeddingConfigService
      embeddingConfigService;
  private final QdrantProperties qdrantProperties;
  private final ObjectMapper objectMapper;

  public KnowledgeIndexingService(
      KnowledgeDocumentMapper documentMapper,
      KnowledgeDocumentChunkMapper chunkMapper,
      KnowledgeDocumentTagMapper documentTagMapper,
      TagMapper tagMapper,
      TextChunker textChunker,
      EmbeddingClient embeddingClient,
      QdrantClient qdrantClient,
      knowflow.sanjin.modules.embeddingconfig.service.EmbeddingConfigService embeddingConfigService,
      QdrantProperties qdrantProperties) {
    this.documentMapper = documentMapper;
    this.chunkMapper = chunkMapper;
    this.documentTagMapper = documentTagMapper;
    this.tagMapper = tagMapper;
    this.textChunker = textChunker;
    this.embeddingClient = embeddingClient;
    this.qdrantClient = qdrantClient;
    this.embeddingConfigService = embeddingConfigService;
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
        "Indexing task {} document {} key {} payloadOnly={} delete={}",
        task.getId(),
        itemId,
        businessKey,
        payloadOnly,
        deleteTask);

    if (deleteTask) {
      int deleteThroughVersion =
          taskContentVersion(businessKey, KnowledgeConstants.BUSINESS_KEY_DELETE_SUFFIX);
      KnowledgeDocument current = documentMapper.selectById(itemId);
      if (deleteThroughVersion == 0) {
        // 兼容修复前已创建的 :0:DELETE 任务：仅当 Document 仍为删除态时执行，恢复后到达的旧任务直接跳过。
        if (current == null || Boolean.TRUE.equals(current.getDeleted())) {
          deleteAllPoints(itemId);
        }
      } else {
        deletePointsThroughVersion(itemId, deleteThroughVersion);
      }
      return;
    }

    KnowledgeDocument document = documentMapper.selectById(itemId);
    if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
      throw new TerminalIndexException(
          ErrorCode.INDEX_SCHEMA_FAILURE, "KnowledgeDocument " + itemId + " no longer active");
    }

    if (payloadOnly) {
      String collection = qdrantProperties.getCollectionName();
      log.info("Ensuring Qdrant collection {}", collection);
      qdrantClient.ensureCollection(
          collection, embeddingConfigService.getCurrentSnapshot().dimension());
      updatePayloadOnly(document);
      return;
    }

    int version = taskContentVersion(businessKey, null);
    if (!Objects.equals(document.getContentVersion(), version)) {
      log.info(
          "Skipping superseded index task {} for document {} version {}; current version is {}",
          task.getId(),
          itemId,
          version,
          document.getContentVersion());
      return;
    }
    String collection = qdrantProperties.getCollectionName();
    log.info("Ensuring Qdrant collection {}", collection);
    qdrantClient.ensureCollection(
        collection, embeddingConfigService.getCurrentSnapshot().dimension());
    log.info("Indexing document {} contentVersion {}", itemId, version);
    indexVersion(document, version, collection);
  }

  /** 删除 Document 在删除动作发生时已经存在的 Qdrant Point；恢复后更高版本的 Point 不受迟到删除任务影响。 */
  private void deletePointsThroughVersion(long itemId, int deleteThroughVersion) {
    java.util.Map<String, Object> filter =
        java.util.Map.of(
            "must",
            List.of(
                java.util.Map.of(
                    "key", "knowledge_document_id", "match", java.util.Map.of("value", itemId)),
                java.util.Map.of(
                    "key",
                    "content_version",
                    "range",
                    java.util.Map.of("lte", deleteThroughVersion))));
    qdrantClient.deletePointsByFilter(qdrantProperties.getCollectionName(), filter);
  }

  private void deleteAllPoints(long itemId) {
    java.util.Map<String, Object> filter =
        java.util.Map.of(
            "must",
            List.of(
                java.util.Map.of(
                    "key", "knowledge_document_id", "match", java.util.Map.of("value", itemId))));
    qdrantClient.deletePointsByFilter(qdrantProperties.getCollectionName(), filter);
  }

  private void updatePayloadOnly(KnowledgeDocument document) {
    // 仅更新现有 Point 的 payload；若无已索引版本则跳过（FULL 任务会覆盖）
    Integer indexedVersion = document.getIndexedVersion();
    if (indexedVersion == null) {
      return;
    }
    List<KnowledgeDocumentChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocumentChunk>()
                .eq(KnowledgeDocumentChunk::getKnowledgeDocumentId, document.getId())
                .eq(KnowledgeDocumentChunk::getContentVersion, indexedVersion));
    if (chunks.isEmpty()) {
      return;
    }
    List<String> pointIds =
        chunks.stream().map(c -> pointId(document, indexedVersion, c.getChunkIndex())).toList();
    ObjectNode payload = payload(document, indexedVersion, 0);
    qdrantClient.setPayload(qdrantProperties.getCollectionName(), pointIds, payload);
  }

  private void indexVersion(KnowledgeDocument document, int version, String collection) {
    List<TextChunker.Chunk> chunks = textChunker.chunk(document.getTitle(), document.getContent());
    if (chunks.isEmpty()) {
      throw new TerminalIndexException(
          ErrorCode.CHUNK_EMPTY, "No chunks produced for KnowledgeDocument " + document.getId());
    }

    // 幂等：重试或重复投递时先清掉该版本已有 chunk，再重新落库，避免唯一约束冲突
    chunkMapper.delete(
        new LambdaQueryWrapper<KnowledgeDocumentChunk>()
            .eq(KnowledgeDocumentChunk::getKnowledgeDocumentId, document.getId())
            .eq(KnowledgeDocumentChunk::getContentVersion, version));

    // 保存 Chunk 到 MySQL（事实源）
    List<String> embeddingTexts = new ArrayList<>();
    List<KnowledgeDocumentChunk> savedChunks = new ArrayList<>();
    int index = 0;
    for (TextChunker.Chunk chunk : chunks) {
      KnowledgeDocumentChunk kc = new KnowledgeDocumentChunk();
      kc.setKnowledgeDocumentId(document.getId());
      kc.setOwnerId(document.getOwnerId());
      kc.setContentVersion(version);
      kc.setChunkIndex(index);
      kc.setChunkId(chunkId(document, version, index));
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
    List<float[]> vectors =
        embeddingClient.embed(embeddingTexts, embeddingConfigService.getCurrentSnapshot());
    log.info("Embedded {} vectors", vectors.size());

    // Qdrant Upsert
    List<QdrantClient.Point> points = new ArrayList<>();
    for (int i = 0; i < savedChunks.size(); i++) {
      KnowledgeDocumentChunk kc = savedChunks.get(i);
      points.add(
          new QdrantClient.Point(
              pointId(document, version, kc.getChunkIndex()),
              vectors.get(i),
              payload(document, version, kc.getChunkIndex())));
    }
    qdrantClient.upsertPoints(collection, points);
    log.info("Upserted {} points to Qdrant", points.size());

    // 成功后切换当前索引版本
    documentMapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeDocument>()
            .eq(KnowledgeDocument::getId, document.getId())
            .eq(KnowledgeDocument::getContentVersion, version)
            .set(KnowledgeDocument::getIndexedVersion, version)
            .set(KnowledgeDocument::getIndexStatus, KnowledgeConstants.INDEX_INDEXED)
            .set(KnowledgeDocument::getIndexErrorCode, null)
            .set(KnowledgeDocument::getIndexErrorMessage, null));

    // 清理旧版本 Point（content_version < 当前）
    java.util.Map<String, Object> oldFilter =
        java.util.Map.of(
            "must",
            List.of(
                java.util.Map.of(
                    "key",
                    "knowledge_document_id",
                    "match",
                    java.util.Map.of("value", document.getId())),
                java.util.Map.of(
                    "key", "content_version", "range", java.util.Map.of("lt", version))));
    qdrantClient.deletePointsByFilter(collection, oldFilter);
  }

  private int taskContentVersion(String businessKey, String suffix) {
    // 格式：KNOWLEDGE_DOCUMENT:{documentId}:{contentVersion} 或
    // KNOWLEDGE_DOCUMENT:{documentId}:{contentVersion}:PAYLOAD
    String key = businessKey;
    if (suffix != null && businessKey.endsWith(suffix)) {
      key = businessKey.substring(0, businessKey.length() - suffix.length());
    } else if (businessKey.endsWith(KnowledgeConstants.BUSINESS_KEY_PAYLOAD_SUFFIX)) {
      key =
          businessKey.substring(
              0, businessKey.length() - KnowledgeConstants.BUSINESS_KEY_PAYLOAD_SUFFIX.length());
    }
    String[] parts = key.split(KnowledgeConstants.BUSINESS_KEY_DELIMITER);
    int version;
    try {
      version = Integer.parseInt(parts[parts.length - 1]);
    } catch (RuntimeException e) {
      throw new RetryableIndexException(
          ErrorCode.INDEX_SCHEMA_FAILURE, "Unparseable business key " + businessKey);
    }
    return version;
  }

  /** 确定性 Chunk ID：ownerId:documentId:contentVersion:chunkIndex 的 UUID v5。 */
  private String chunkId(KnowledgeDocument document, int version, int index) {
    return uuid5("chunk:" + document.getId() + ":" + version + ":" + index);
  }

  /** 确定性 Qdrant Point ID。 */
  private String pointId(KnowledgeDocument document, int version, int index) {
    return uuid5("point:" + document.getId() + ":" + version + ":" + index);
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

  /** Qdrant Payload：不含完整正文，仅检索所需 metadata 与单归属/标签。 */
  private ObjectNode payload(KnowledgeDocument document, int version, int chunkIndex) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("user_id", document.getOwnerId());
    payload.put("knowledge_document_id", document.getId());
    payload.put("content_version", version);
    payload.put("chunk_index", chunkIndex);
    payload.put("chunk_id", chunkId(document, version, chunkIndex));
    payload.put("source_type", document.getSourceType());
    payload.put("knowledge_base_id", document.getKbId());

    ArrayNode tags = payload.putArray("tags");
    documentTagMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeDocumentTag>()
                .eq(KnowledgeDocumentTag::getKnowledgeDocumentId, document.getId())
                .eq(KnowledgeDocumentTag::getDeleted, false))
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
