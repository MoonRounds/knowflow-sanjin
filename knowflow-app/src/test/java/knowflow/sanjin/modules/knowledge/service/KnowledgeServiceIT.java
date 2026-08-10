package knowflow.sanjin.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.UUID;
import knowflow.sanjin.modules.knowledge.dto.CreateManualNoteRequest;
import knowflow.sanjin.modules.knowledge.dto.UpdateManualNoteRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeBaseItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeItemNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeItemVersionConflictException;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeBaseItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemTagMapper;
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
@DisplayName("KnowledgeService Manual Note Integration Tests")
class KnowledgeServiceIT extends MySQLTestBase {

  @Autowired private KnowledgeService service;

  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Autowired private KnowledgeItemMapper itemMapper;

  @Autowired private KnowledgeBaseItemMapper kbItemMapper;

  @Autowired private KnowledgeItemTagMapper itemTagMapper;

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

  private CreateManualNoteRequest createRequest() {
    CreateManualNoteRequest req = new CreateManualNoteRequest();
    req.setTitle("My Note");
    req.setSummary("A summary");
    req.setContent("# Heading\n\nSome body text here.");
    req.setKnowledgeBaseIds(List.of(kbId.toString()));
    req.setTags(List.of("ai", " notes "));
    return req;
  }

  @Test
  @DisplayName("should create Manual Note with relations, tags and a PENDING index task")
  void shouldCreateManualNote() {
    KnowledgeItem item = service.createManualNote(createRequest());

    assertThat(item.getId()).isNotNull();
    assertThat(item.getSourceType()).isEqualTo("MANUAL_NOTE");
    assertThat(item.getContentVersion()).isEqualTo(1);
    assertThat(item.getIndexStatus()).isEqualTo("PENDING");
    assertThat(item.getStatus()).isEqualTo("ACTIVE");
    assertThat(item.getOwnerId()).isEqualTo(currentOwnerProvider.getCurrentOwnerId());
    assertThat(item.getTitle()).isEqualTo("My Note");

    assertThat(service.getKnowledgeBaseIds(item.getId())).containsExactly(kbId);
    assertThat(service.getTagNames(item.getId())).containsExactly("ai", "notes");

    ProcessingTask task =
        processingTaskMapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getBusinessId, item.getId()));
    assertThat(task).isNotNull();
    assertThat(task.getTaskType()).isEqualTo(ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX);
    assertThat(task.getBusinessKey()).isEqualTo("KNOWLEDGE_ITEM:" + item.getId() + ":1");
    assertThat(task.getStatus()).isEqualTo(ProcessingConstants.STATUS_PENDING);
  }

  @Test
  @DisplayName("should derive title from first content line when title missing")
  void shouldDeriveTitleFromContent() {
    CreateManualNoteRequest req = createRequest();
    req.setTitle(null);
    req.setContent("First line\n\nSecond line");
    KnowledgeItem item = service.createManualNote(req);
    assertThat(item.getTitle()).isEqualTo("First line");
  }

  @Test
  @DisplayName("should reject creating a Manual Note with zero KnowledgeBase")
  void shouldRejectZeroKnowledgeBase() {
    CreateManualNoteRequest req = createRequest();
    req.setKnowledgeBaseIds(List.of());
    assertThatThrownBy(() -> service.createManualNote(req))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should bump contentVersion and create a new FULL task when content changes")
  void shouldCreateNewVersionOnContentEdit() {
    KnowledgeItem created = service.createManualNote(createRequest());
    assertThat(created.getContentVersion()).isEqualTo(1);

    UpdateManualNoteRequest update = new UpdateManualNoteRequest();
    update.setContent("## New\n\nUpdated body.");
    update.setKnowledgeBaseIds(List.of(kbId.toString()));
    update.setRowVersion(created.getRowVersion());

    KnowledgeItem updated = service.updateManualNote(created.getId(), update);
    assertThat(updated.getContentVersion()).isEqualTo(2);
    assertThat(updated.getIndexStatus()).isEqualTo("PENDING");

    // 新版本 FULL 任务：businessKey = KNOWLEDGE_ITEM:<id>:2
    ProcessingTask task =
        processingTaskMapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getBusinessId, created.getId())
                .eq(ProcessingTask::getBusinessKey, "KNOWLEDGE_ITEM:" + created.getId() + ":2"));
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(ProcessingConstants.STATUS_PENDING);
  }

  @Test
  @DisplayName("should create a PAYLOAD task when only relations change")
  void shouldCreatePayloadTaskOnRelationChange() {
    // 第二个 KB
    CreateKnowledgeBaseRequest second = new CreateKnowledgeBaseRequest();
    second.setName("Second KB " + UUID.randomUUID());
    KnowledgeBase secondKb = knowledgeBaseService.create(second);

    KnowledgeItem created = service.createManualNote(createRequest());

    UpdateManualNoteRequest update = new UpdateManualNoteRequest();
    update.setContent(created.getContent()); // content unchanged
    update.setKnowledgeBaseIds(List.of(kbId.toString(), secondKb.getId().toString()));
    update.setRowVersion(created.getRowVersion());

    KnowledgeItem updated = service.updateManualNote(created.getId(), update);
    // 仅关系变化：contentVersion 不递增
    assertThat(updated.getContentVersion()).isEqualTo(1);

    ProcessingTask payloadTask =
        processingTaskMapper.selectOne(
            new LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getBusinessId, created.getId())
                .eq(
                    ProcessingTask::getBusinessKey,
                    "KNOWLEDGE_ITEM:" + created.getId() + ":1:PAYLOAD"));
    assertThat(payloadTask).isNotNull();
    assertThat(service.getKnowledgeBaseIds(created.getId()))
        .containsExactlyInAnyOrder(kbId, secondKb.getId());
  }

  @Test
  @DisplayName("should throw version conflict when updating with stale rowVersion")
  void shouldThrowVersionConflict() {
    KnowledgeItem created = service.createManualNote(createRequest());

    UpdateManualNoteRequest first = new UpdateManualNoteRequest();
    first.setContent("v2 body");
    first.setKnowledgeBaseIds(List.of(kbId.toString()));
    first.setRowVersion(created.getRowVersion());
    service.updateManualNote(created.getId(), first);

    UpdateManualNoteRequest stale = new UpdateManualNoteRequest();
    stale.setContent("stale body");
    stale.setKnowledgeBaseIds(List.of(kbId.toString()));
    stale.setRowVersion(created.getRowVersion()); // old version

    assertThatThrownBy(() -> service.updateManualNote(created.getId(), stale))
        .isInstanceOf(KnowledgeItemVersionConflictException.class);
  }

  @Test
  @DisplayName("should isolate items across owners")
  void shouldIsolateAcrossOwners() {
    // 第二个 owner 的 Item
    AppUser secondOwner = new AppUser();
    secondOwner.setName("Second Owner");
    secondOwner.setStatus("ACTIVE");
    appUserMapper.insert(secondOwner);

    KnowledgeItem other = new KnowledgeItem();
    other.setOwnerId(secondOwner.getId());
    other.setSourceType("MANUAL_NOTE");
    other.setTitle("Other Owner Note");
    other.setContent("private");
    other.setContentVersion(1);
    other.setIndexStatus("PENDING");
    other.setStatus("ACTIVE");
    other.setRowVersion(0);
    itemMapper.insert(other);

    assertThatThrownBy(() -> service.getByIdAndOwner(other.getId()))
        .isInstanceOf(KnowledgeItemNotFoundException.class);

    UpdateManualNoteRequest update = new UpdateManualNoteRequest();
    update.setContent("hacked");
    update.setKnowledgeBaseIds(List.of(kbId.toString()));
    update.setRowVersion(0);
    assertThatThrownBy(() -> service.updateManualNote(other.getId(), update))
        .isInstanceOf(KnowledgeItemNotFoundException.class);
  }

  @Test
  @DisplayName("should restore a soft-deleted relation when re-linked")
  void shouldRestoreSoftDeletedRelation() {
    KnowledgeItem created = service.createManualNote(createRequest());

    // 软删关系后重新关联，应恢复 deleted=0 而非插入重复
    kbItemMapper.update(
        null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeBaseItem>()
            .eq(KnowledgeBaseItem::getKnowledgeItemId, created.getId())
            .set(KnowledgeBaseItem::getDeleted, true));

    KnowledgeBaseItem rel =
        kbItemMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeBaseItem>()
                .eq(KnowledgeBaseItem::getKnowledgeItemId, created.getId()));
    assertThat(rel.getDeleted()).isTrue();

    // 重新创建关联（同 contentVersion 的 PAYLOAD 任务幂等跳过，无需关心）
    service.updateManualNote(
        created.getId(), createUpdateWithSameContent(created, List.of(kbId.toString())));

    KnowledgeBaseItem restored =
        kbItemMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeBaseItem>()
                .eq(KnowledgeBaseItem::getKnowledgeItemId, created.getId()));
    assertThat(restored.getDeleted()).isFalse();
  }

  private UpdateManualNoteRequest createUpdateWithSameContent(
      KnowledgeItem item, List<String> kbIds) {
    UpdateManualNoteRequest update = new UpdateManualNoteRequest();
    update.setContent(item.getContent());
    update.setKnowledgeBaseIds(kbIds);
    update.setRowVersion(item.getRowVersion());
    return update;
  }

  @Test
  @DisplayName("should deduplicate tags by normalized name")
  void shouldDeduplicateTags() {
    CreateManualNoteRequest req = createRequest();
    req.setTags(List.of("AI", " ai ", "Notes", "notes"));
    KnowledgeItem item = service.createManualNote(req);
    assertThat(service.getTagNames(item.getId())).containsExactlyInAnyOrder("ai", "notes");
  }
}
