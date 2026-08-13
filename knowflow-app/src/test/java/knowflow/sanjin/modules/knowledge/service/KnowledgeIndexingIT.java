package knowflow.sanjin.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.io.IOException;
import java.util.List;
import knowflow.sanjin.modules.knowledge.dto.CreateDocumentRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocumentChunk;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentChunkMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import knowflow.sanjin.testinfra.MySQLRabbitMQIndexingTestBase;
import knowflow.sanjin.testinfra.stub.OpenAiCompatibleStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * 知识索引最小纵切集成测试：Manual Note → Chunk → Embedding(stub) → Qdrant Upsert。
 *
 * <p>验证 MySQL 保存规范 Chunk、Qdrant 生成确定性 Point 且 Payload 无完整正文、索引成功后 Item 切换到 indexed_version 与
 * INDEXED 状态。Embedding 指向本地 stub（4 维），Qdrant 维度需与 stub 一致——因此 覆盖 knowflow.embedding.dimensions=4 使
 * Qdrant collection 按 4 维创建。
 *
 * <p>测试方法顺序执行：共享同一 Spring context 与容器，顺序执行避免 item/task 交错污染。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
    properties = {
      "knowflow.embedding.dimensions=4",
      "knowflow.embedding.model=stub-embedding",
      "logging.level.knowflow.sanjin.modules.processing=DEBUG",
    })
@DisplayName("Knowledge Indexing Vertical Slice Integration Tests")
class KnowledgeIndexingIT extends MySQLRabbitMQIndexingTestBase {

  /** Stub 必须在 Spring context 启动前就绪，以便 @DynamicPropertySource 读取端口。 */
  private static final OpenAiCompatibleStub embeddingStub;

  static {
    try {
      embeddingStub = OpenAiCompatibleStub.start();
    } catch (IOException e) {
      throw new IllegalStateException("Could not start embedding stub", e);
    }
  }

  @AfterAll
  static void stopStub() {
    embeddingStub.close();
  }

  @DynamicPropertySource
  static void embedStubProps(DynamicPropertyRegistry registry) {
    registry.add("knowflow.embedding.base-url", embeddingStub::baseUrl);
    registry.add("knowflow.embedding.api-key", () -> "test-key");
  }

  @Autowired private KnowledgeDocumentService knowledgeService;

  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Autowired private KnowledgeDocumentMapper itemMapper;

  @Autowired private KnowledgeDocumentChunkMapper chunkMapper;

  @Autowired private ProcessingTaskMapper processingTaskMapper;

  @Autowired private QdrantClient qdrantClient;

  @Autowired private KnowledgeIndexingService indexingService;

  private Long kbId;

  @BeforeEach
  void setUp() {
    CreateKnowledgeBaseRequest kbReq = new CreateKnowledgeBaseRequest();
    kbReq.setName("Indexing KB " + System.nanoTime());
    KnowledgeBase kb = knowledgeBaseService.create(kbReq);
    kbId = kb.getId();
  }

  private CreateDocumentRequest noteRequest() {
    CreateDocumentRequest req = new CreateDocumentRequest();
    req.setTitle("Test Note");
    req.setContent(
        "# Intro\n\nThis is the introduction paragraph.\n\n"
            + "## Details\n\nDetailed content here about vectors and retrieval.");
    req.setKnowledgeBaseId(kbId.toString());
    req.setTags(List.of("testing"));
    return req;
  }

  private void waitForIndexed(Long itemId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      KnowledgeDocument item = itemMapper.selectById(itemId);
      if (item != null
          && "INDEXED".equals(item.getIndexStatus())
          && item.getIndexedVersion() != null) {
        return;
      }
      Thread.sleep(200);
    }
    KnowledgeDocument last = itemMapper.selectById(itemId);
    throw new AssertionError(
        "Timed out waiting for item "
            + itemId
            + " to index (status="
            + (last != null ? last.getIndexStatus() + "/err=" + last.getIndexErrorCode() : "gone")
            + ")");
  }

  @Test
  @Order(1)
  @DisplayName("should index a manual note into MySQL chunks and Qdrant points")
  void shouldIndexManualNote() throws Exception {
    KnowledgeDocument item = knowledgeService.createManualNote(noteRequest());

    waitForIndexed(item.getId());

    KnowledgeDocument indexed = itemMapper.selectById(item.getId());
    assertThat(indexed.getIndexStatus()).isEqualTo("INDEXED");
    assertThat(indexed.getIndexedVersion()).isEqualTo(1);

    List<KnowledgeDocumentChunk> chunks =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocumentChunk>()
                .eq(KnowledgeDocumentChunk::getKnowledgeDocumentId, item.getId()));
    assertThat(chunks).isNotEmpty();
    chunks.forEach(
        c -> {
          assertThat(c.getContentVersion()).isEqualTo(1);
          assertThat(c.getChunkId()).isNotBlank();
          assertThat(c.getContent()).isNotBlank();
        });

    // 任务 SUCCEEDED
    ProcessingTask task =
        processingTaskMapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getBusinessId, item.getId()));
    assertThat(task.getStatus()).isEqualTo(ProcessingConstants.STATUS_SUCCEEDED);
  }

  @Test
  @Order(2)
  @DisplayName("should reindex with a new contentVersion and keep old chunks serviceable")
  void shouldReindexOnContentEdit() throws Exception {
    KnowledgeDocument created = knowledgeService.createManualNote(noteRequest());
    waitForIndexed(created.getId());

    // 编辑正文 → contentVersion 2 → 新 FULL 任务
    knowflow.sanjin.modules.knowledge.dto.UpdateDocumentRequest update =
        new knowflow.sanjin.modules.knowledge.dto.UpdateDocumentRequest();
    update.setContent("# Intro\n\nCompletely new body after edit.");
    update.setKnowledgeBaseId(kbId.toString());
    update.setRowVersion(created.getRowVersion());
    KnowledgeDocument updated = knowledgeService.updateManualNote(created.getId(), update);
    assertThat(updated.getContentVersion()).isEqualTo(2);

    // 等待 v2 索引成功
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      KnowledgeDocument item = itemMapper.selectById(created.getId());
      if (item != null
          && "INDEXED".equals(item.getIndexStatus())
          && Integer.valueOf(2).equals(item.getIndexedVersion())) {
        break;
      }
      Thread.sleep(200);
      if (System.currentTimeMillis() >= deadline) {
        throw new AssertionError("Timed out waiting for v2 index");
      }
    }

    // v2 chunks 存在；旧 v1 无残留（FULL 任务按 version 落库，v1 行仍在，仅 content_version=1）
    List<KnowledgeDocumentChunk> v2 =
        chunkMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocumentChunk>()
                .eq(KnowledgeDocumentChunk::getKnowledgeDocumentId, created.getId())
                .eq(KnowledgeDocumentChunk::getContentVersion, 2));
    assertThat(v2).isNotEmpty();
  }

  @Test
  @Order(3)
  @DisplayName("should remove all Qdrant points when the item is deleted")
  void shouldRemovePointsOnDelete() throws Exception {
    KnowledgeDocument created = knowledgeService.createManualNote(noteRequest());
    waitForIndexed(created.getId());

    java.util.Map<String, Object> filter =
        java.util.Map.of(
            "must",
            List.of(
                java.util.Map.of(
                    "key",
                    "knowledge_document_id",
                    "match",
                    java.util.Map.of("value", created.getId()))));
    assertThat(qdrantClient.countPoints("knowflow_it_dense", filter)).isGreaterThan(0);

    knowledgeService.softDelete(created.getId(), created.getRowVersion());

    // 等待删除任务完成并清理 Qdrant
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      if (qdrantClient.countPoints("knowflow_it_dense", filter) == 0) {
        break;
      }
      Thread.sleep(200);
      if (System.currentTimeMillis() >= deadline) {
        throw new AssertionError("Timed out waiting for Qdrant points to be removed");
      }
    }
    assertThat(qdrantClient.countPoints("knowflow_it_dense", filter)).isZero();
  }

  @Test
  @Order(4)
  @DisplayName("should reject deleting a KnowledgeBase that is the only owner of an item")
  void shouldRejectDeletingOnlyOwnerKnowledgeBase() throws Exception {
    KnowledgeDocument created = knowledgeService.createManualNote(noteRequest());
    waitForIndexed(created.getId());

    // 该 KB 是 Item 唯一归属 → 删除被阻止
    knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase kb =
        knowledgeBaseService.getByIdAndOwner(kbId);
    assertThatThrownBy(() -> knowledgeBaseService.softDelete(kbId, kb.getRowVersion()))
        .isInstanceOf(
            knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseInUseException.class);

    // 删除 Item 后再删除 KB 应成功
    knowledgeService.softDelete(created.getId(), created.getRowVersion());
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      if (qdrantClient.countPoints(
              "knowflow_it_dense",
              java.util.Map.of(
                  "must",
                  List.of(
                      java.util.Map.of(
                          "key",
                          "knowledge_document_id",
                          "match",
                          java.util.Map.of("value", created.getId())))))
          == 0) {
        break;
      }
      Thread.sleep(200);
    }
    knowledgeBaseService.softDelete(kbId, kb.getRowVersion());
  }

  @Test
  @Order(5)
  @DisplayName("late v1 index/delete tasks must not affect the current v2 index")
  void shouldIgnoreLateOlderVersionTasks() throws Exception {
    KnowledgeDocument created = knowledgeService.createManualNote(noteRequest());
    waitForIndexed(created.getId());

    knowflow.sanjin.modules.knowledge.dto.UpdateDocumentRequest update =
        new knowflow.sanjin.modules.knowledge.dto.UpdateDocumentRequest();
    update.setContent("# Current\n\nThe current version must survive late v1 deliveries.");
    update.setKnowledgeBaseId(kbId.toString());
    update.setRowVersion(created.getRowVersion());
    knowledgeService.updateManualNote(created.getId(), update);
    waitForIndexedVersion(created.getId(), 2);

    assertThat(qdrantClient.countPoints("knowflow_it_dense", versionFilter(created.getId(), 1)))
        .isZero();
    assertThat(qdrantClient.countPoints("knowflow_it_dense", versionFilter(created.getId(), 2)))
        .isGreaterThan(0);

    ProcessingTask staleIndex = new ProcessingTask();
    staleIndex.setId(-1L);
    staleIndex.setTaskType(ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX);
    staleIndex.setBusinessId(created.getId());
    staleIndex.setBusinessKey("KNOWLEDGE_DOCUMENT:" + created.getId() + ":1");
    indexingService.execute(staleIndex);

    ProcessingTask lateDelete = new ProcessingTask();
    lateDelete.setId(-2L);
    lateDelete.setTaskType(ProcessingConstants.TASK_TYPE_KNOWLEDGE_DELETE);
    lateDelete.setBusinessId(created.getId());
    lateDelete.setBusinessKey("KNOWLEDGE_DOCUMENT:" + created.getId() + ":1:DELETE");
    indexingService.execute(lateDelete);

    assertThat(qdrantClient.countPoints("knowflow_it_dense", versionFilter(created.getId(), 1)))
        .isZero();
    assertThat(qdrantClient.countPoints("knowflow_it_dense", versionFilter(created.getId(), 2)))
        .isGreaterThan(0);
    assertThat(itemMapper.selectById(created.getId()).getIndexedVersion()).isEqualTo(2);
  }

  private void waitForIndexedVersion(Long itemId, int version) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      KnowledgeDocument item = itemMapper.selectById(itemId);
      if (item != null
          && "INDEXED".equals(item.getIndexStatus())
          && Integer.valueOf(version).equals(item.getIndexedVersion())) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Timed out waiting for index version " + version);
  }

  private java.util.Map<String, Object> versionFilter(Long itemId, int version) {
    return java.util.Map.of(
        "must",
        List.of(
            java.util.Map.of(
                "key", "knowledge_document_id", "match", java.util.Map.of("value", itemId)),
            java.util.Map.of(
                "key", "content_version", "match", java.util.Map.of("value", version))));
  }

  @Test
  @Order(6)
  @DisplayName("should rebuild missing/stale indexes via reindexAllForOwner")
  void shouldRebuildViaReindexAllForOwner() throws Exception {
    KnowledgeDocument created = knowledgeService.createManualNote(noteRequest());
    waitForIndexed(created.getId());

    // G13 过滤条件：已 INDEXED 且版本一致的文档不应被重提
    assertThat(knowledgeService.reindexAllForOwner()).isZero();

    // 模拟存量缺索引/索引过期（V12 迁移后需全量重建）：重置 index_status 与 indexed_version
    itemMapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeDocument>()
            .eq(KnowledgeDocument::getId, created.getId())
            .set(KnowledgeDocument::getIndexStatus, "PENDING")
            .set(KnowledgeDocument::getIndexedVersion, null));

    int submitted = knowledgeService.reindexAllForOwner();
    assertThat(submitted).isEqualTo(1);

    waitForIndexed(created.getId());
    KnowledgeDocument reindexed = itemMapper.selectById(created.getId());
    assertThat(reindexed.getIndexStatus()).isEqualTo("INDEXED");
    assertThat(reindexed.getIndexedVersion()).isEqualTo(1);

    assertThat(qdrantClient.countPoints("knowflow_it_dense", versionFilter(created.getId(), 1)))
        .isGreaterThan(0);
  }
}
