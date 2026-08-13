package knowflow.sanjin.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.UUID;
import knowflow.sanjin.modules.knowledge.dto.CreateDocumentRequest;
import knowflow.sanjin.modules.knowledge.dto.UpdateDocumentRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeDocumentNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeDocumentVersionConflictException;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentTagMapper;
import knowflow.sanjin.modules.knowledge.mapper.TagMapper;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.owner.entity.AppUser;
import knowflow.sanjin.modules.owner.mapper.AppUserMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import knowflow.sanjin.modules.processing.service.TaskPublisher;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@DisplayName("KnowledgeDocumentService Manual Note Integration Tests")
class KnowledgeServiceIT extends MySQLTestBase {

  @Autowired private KnowledgeDocumentService service;

  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Autowired private KnowledgeDocumentMapper itemMapper;

  @Autowired private KnowledgeDocumentTagMapper itemTagMapper;

  @Autowired private TagMapper tagMapper;

  @Autowired private ProcessingTaskMapper processingTaskMapper;

  @Autowired private AppUserMapper appUserMapper;

  @Autowired private CurrentOwnerProvider currentOwnerProvider;

  /** 隔离 RabbitMQ：纯 MySQL 测试不真正发布消息。 */
  @MockitoBean private TaskPublisher taskPublisher;

  private Long kbId;

  @BeforeEach
  void setUp() {
    CreateKnowledgeBaseRequest kbReq = new CreateKnowledgeBaseRequest();
    kbReq.setName("Notes KB " + UUID.randomUUID());
    KnowledgeBase kb = knowledgeBaseService.create(kbReq);
    kbId = kb.getId();
  }

  private CreateDocumentRequest createRequest() {
    CreateDocumentRequest req = new CreateDocumentRequest();
    req.setTitle("My Note");
    req.setSummary("A summary");
    req.setContent("# Heading\n\nSome body text here.");
    req.setKnowledgeBaseId(kbId.toString());
    req.setTags(List.of("ai", " notes "));
    return req;
  }

  @Test
  @DisplayName("should create Manual Note with relations, tags and a PENDING index task")
  void shouldCreateManualNote() {
    KnowledgeDocument item = service.createManualNote(createRequest());

    assertThat(item.getId()).isNotNull();
    assertThat(item.getSourceType()).isEqualTo("MANUAL_NOTE");
    assertThat(item.getContentVersion()).isEqualTo(1);
    assertThat(item.getIndexStatus()).isEqualTo("PENDING");
    assertThat(item.getDeleted()).isFalse();
    assertThat(item.getKbId()).isEqualTo(kbId);
    assertThat(item.getOwnerId()).isEqualTo(currentOwnerProvider.getCurrentOwnerId());
    assertThat(item.getTitle()).isEqualTo("My Note");

    assertThat(service.getKnowledgeBaseId(item.getId())).isEqualTo(kbId);
    assertThat(service.getTagNames(item.getId())).containsExactly("ai", "notes");

    ProcessingTask task =
        processingTaskMapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getBusinessId, item.getId()));
    assertThat(task).isNotNull();
    assertThat(task.getTaskType()).isEqualTo(ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX);
    assertThat(task.getBusinessKey()).isEqualTo("KNOWLEDGE_DOCUMENT:" + item.getId() + ":1");
    assertThat(task.getStatus()).isEqualTo(ProcessingConstants.STATUS_PENDING);
  }

  @Test
  @DisplayName("should derive title from first content line when title missing")
  void shouldDeriveTitleFromContent() {
    CreateDocumentRequest req = createRequest();
    req.setTitle(null);
    req.setContent("First line\n\nSecond line");
    KnowledgeDocument item = service.createManualNote(req);
    assertThat(item.getTitle()).isEqualTo("First line");
  }

  @Test
  @DisplayName("should reject creating a Manual Note with zero KnowledgeBase")
  void shouldRejectZeroKnowledgeBase() {
    CreateDocumentRequest req = createRequest();
    req.setKnowledgeBaseId(null);
    assertThatThrownBy(() -> service.createManualNote(req))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should bump contentVersion and create a new FULL task when content changes")
  void shouldCreateNewVersionOnContentEdit() {
    KnowledgeDocument created = service.createManualNote(createRequest());
    assertThat(created.getContentVersion()).isEqualTo(1);

    UpdateDocumentRequest update = new UpdateDocumentRequest();
    update.setContent("## New\n\nUpdated body.");
    update.setKnowledgeBaseId(kbId.toString());
    update.setRowVersion(created.getRowVersion());

    KnowledgeDocument updated = service.updateManualNote(created.getId(), update);
    assertThat(updated.getContentVersion()).isEqualTo(2);
    assertThat(updated.getIndexStatus()).isEqualTo("PENDING");

    // 新版本 FULL 任务：businessKey = KNOWLEDGE_DOCUMENT:<id>:2
    ProcessingTask task =
        processingTaskMapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getBusinessId, created.getId())
                .eq(
                    ProcessingTask::getBusinessKey,
                    "KNOWLEDGE_DOCUMENT:" + created.getId() + ":2"));
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(ProcessingConstants.STATUS_PENDING);
  }

  @Test
  @DisplayName("should create a PAYLOAD task when only the single KnowledgeBase changes")
  void shouldCreatePayloadTaskOnRelationChange() {
    // 第二个 KB：把文档从原 KB 迁移过去（单归属）
    CreateKnowledgeBaseRequest second = new CreateKnowledgeBaseRequest();
    second.setName("Second KB " + UUID.randomUUID());
    KnowledgeBase secondKb = knowledgeBaseService.create(second);

    KnowledgeDocument created = service.createManualNote(createRequest());

    UpdateDocumentRequest update = new UpdateDocumentRequest();
    update.setContent(created.getContent()); // content unchanged
    update.setKnowledgeBaseId(secondKb.getId().toString());
    update.setRowVersion(created.getRowVersion());

    KnowledgeDocument updated = service.updateManualNote(created.getId(), update);
    // 仅 KB 归属变化：contentVersion 不递增
    assertThat(updated.getContentVersion()).isEqualTo(1);

    ProcessingTask payloadTask =
        processingTaskMapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getBusinessId, created.getId())
                .eq(
                    ProcessingTask::getBusinessKey,
                    "KNOWLEDGE_DOCUMENT:" + created.getId() + ":1:PAYLOAD"));
    assertThat(payloadTask).isNotNull();
    // 单归属：文档现在只属于第二个 KB
    assertThat(service.getKnowledgeBaseId(created.getId())).isEqualTo(secondKb.getId());
  }

  @Test
  @DisplayName("should throw version conflict when updating with stale rowVersion")
  void shouldThrowVersionConflict() {
    KnowledgeDocument created = service.createManualNote(createRequest());

    UpdateDocumentRequest first = new UpdateDocumentRequest();
    first.setContent("v2 body");
    first.setKnowledgeBaseId(kbId.toString());
    first.setRowVersion(created.getRowVersion());
    service.updateManualNote(created.getId(), first);

    UpdateDocumentRequest stale = new UpdateDocumentRequest();
    stale.setContent("stale body");
    stale.setKnowledgeBaseId(kbId.toString());
    stale.setRowVersion(created.getRowVersion()); // old version

    assertThatThrownBy(() -> service.updateManualNote(created.getId(), stale))
        .isInstanceOf(KnowledgeDocumentVersionConflictException.class);
  }

  @Test
  @DisplayName("should isolate items across owners")
  void shouldIsolateAcrossOwners() {
    // 第二个 owner 的 Item
    AppUser secondOwner = new AppUser();
    secondOwner.setName("Second Owner");
    secondOwner.setStatus("ACTIVE");
    appUserMapper.insert(secondOwner);

    KnowledgeDocument other = new KnowledgeDocument();
    other.setOwnerId(secondOwner.getId());
    other.setKbId(kbId);
    other.setSourceType("MANUAL_NOTE");
    other.setTitle("Other Owner Note");
    other.setContent("private");
    other.setContentVersion(1);
    other.setIndexStatus("PENDING");
    other.setDeleted(false);
    other.setRowVersion(0);
    itemMapper.insert(other);

    assertThatThrownBy(() -> service.getByIdAndOwner(other.getId()))
        .isInstanceOf(KnowledgeDocumentNotFoundException.class);

    UpdateDocumentRequest update = new UpdateDocumentRequest();
    update.setContent("hacked");
    update.setKnowledgeBaseId(kbId.toString());
    update.setRowVersion(0);
    assertThatThrownBy(() -> service.updateManualNote(other.getId(), update))
        .isInstanceOf(KnowledgeDocumentNotFoundException.class);
  }

  @Test
  @DisplayName("should soft-delete a document and hide it from listForOwner")
  void shouldSoftDeleteMarksDeletedAndExcludesFromList() {
    KnowledgeDocument created = service.createManualNote(createRequest());

    service.softDelete(created.getId(), created.getRowVersion());

    KnowledgeDocument deletedRow = itemMapper.selectById(created.getId());
    assertThat(deletedRow.getDeleted()).isTrue();
    assertThat(service.listForOwner()).noneMatch(d -> d.getId().equals(created.getId()));

    // 软删触发 DELETE 任务（contentVersion=1）
    ProcessingTask deleteTask =
        processingTaskMapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getBusinessId, created.getId())
                .eq(
                    ProcessingTask::getBusinessKey,
                    "KNOWLEDGE_DOCUMENT:" + created.getId() + ":1:DELETE"));
    assertThat(deleteTask).isNotNull();
  }

  @Test
  @DisplayName("should deduplicate tags by normalized name")
  void shouldDeduplicateTags() {
    CreateDocumentRequest req = createRequest();
    req.setTags(List.of("AI", " ai ", "Notes", "notes"));
    KnowledgeDocument item = service.createManualNote(req);
    assertThat(service.getTagNames(item.getId())).containsExactlyInAnyOrder("ai", "notes");
  }
}
