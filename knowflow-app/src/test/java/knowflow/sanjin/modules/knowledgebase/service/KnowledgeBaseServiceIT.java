package knowflow.sanjin.modules.knowledgebase.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseInUseException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNameConflictException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNotFoundException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseVersionConflictException;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.owner.entity.AppUser;
import knowflow.sanjin.modules.owner.mapper.AppUserMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("KnowledgeBase CRUD Integration Tests")
class KnowledgeBaseServiceIT extends MySQLTestBase {

  @Autowired private KnowledgeBaseService service;

  @Autowired private CurrentOwnerProvider currentOwnerProvider;

  @Autowired private AppUserMapper appUserMapper;

  @Autowired private KnowledgeBaseMapper knowledgeBaseMapper;

  @Autowired private KnowledgeDocumentMapper documentMapper;

  private Long createdId;

  @Test
  @DisplayName("should create KnowledgeBase and return with generated id")
  void shouldCreate() {
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("Test KB");
    req.setDescription("A test knowledge base");

    KnowledgeBase kb = service.create(req);

    assertThat(kb.getId()).isNotNull();
    assertThat(kb.getDisplayName()).isEqualTo("Test KB");
    assertThat(kb.getNormalizedName()).isEqualTo("test kb");
    assertThat(kb.getOwnerId()).isEqualTo(currentOwnerProvider.getCurrentOwnerId());
    assertThat(kb.getEnabled()).isTrue();
    assertThat(kb.getDeleted()).isFalse();
    assertThat(kb.getRowVersion()).isEqualTo(0);

    createdId = kb.getId();
  }

  @Test
  @DisplayName("should list KnowledgeBases for the current owner")
  void shouldListForOwner() {
    // 先创建一个，确保有数据
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("List Test KB");
    service.create(req);

    List<KnowledgeBase> list = service.listForOwner();
    assertThat(list).isNotEmpty();
    list.forEach(
        kb -> {
          assertThat(kb.getOwnerId()).isEqualTo(currentOwnerProvider.getCurrentOwnerId());
          assertThat(kb.getDeleted()).isFalse();
        });
  }

  @Test
  @DisplayName("should get KnowledgeBase by id and owner")
  void shouldGetByIdAndOwner() {
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("Get Test KB");
    KnowledgeBase created = service.create(req);

    KnowledgeBase found = service.getByIdAndOwner(created.getId());
    assertThat(found).isNotNull();
    assertThat(found.getDisplayName()).isEqualTo("Get Test KB");
  }

  @Test
  @DisplayName("should throw not found for non-existent KnowledgeBase")
  void shouldThrowNotFound() {
    assertThatThrownBy(() -> service.getByIdAndOwner(99999L))
        .isInstanceOf(KnowledgeBaseNotFoundException.class);
  }

  @Test
  @DisplayName("should reject duplicate normalized name for same owner")
  void shouldRejectDuplicateName() {
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("Duplicate KB");
    service.create(req);

    CreateKnowledgeBaseRequest duplicate = new CreateKnowledgeBaseRequest();
    duplicate.setName("  duplicate kb  "); // different case and spaces -> same normalized

    assertThatThrownBy(() -> service.create(duplicate))
        .isInstanceOf(KnowledgeBaseNameConflictException.class);
  }

  @Test
  @DisplayName("should update KnowledgeBase fields")
  void shouldUpdate() {
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("Update Test KB");
    KnowledgeBase created = service.create(req);

    UpdateKnowledgeBaseRequest update = new UpdateKnowledgeBaseRequest();
    update.setName("Updated KB");
    update.setDescription("Updated description");
    update.setRowVersion(created.getRowVersion());

    KnowledgeBase updated = service.update(created.getId(), update);
    assertThat(updated.getDisplayName()).isEqualTo("Updated KB");
    assertThat(updated.getDescription()).isEqualTo("Updated description");
    // 更新后 rowVersion 应递增
    assertThat(updated.getRowVersion()).isGreaterThan(0);
  }

  @Test
  @DisplayName("should throw version conflict when updating with stale rowVersion")
  void shouldThrowVersionConflict() {
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("Version Test KB");
    KnowledgeBase created = service.create(req);

    UpdateKnowledgeBaseRequest update = new UpdateKnowledgeBaseRequest();
    update.setName("First Update");
    update.setRowVersion(created.getRowVersion());
    service.update(created.getId(), update);

    // 第二次用过期版本更新
    UpdateKnowledgeBaseRequest staleUpdate = new UpdateKnowledgeBaseRequest();
    staleUpdate.setName("Stale Update");
    staleUpdate.setRowVersion(created.getRowVersion()); // old version

    assertThatThrownBy(() -> service.update(created.getId(), staleUpdate))
        .isInstanceOf(KnowledgeBaseVersionConflictException.class);
  }

  @Test
  @DisplayName("should soft delete KnowledgeBase")
  void shouldSoftDelete() {
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("Delete Test KB");
    KnowledgeBase created = service.create(req);

    service.softDelete(created.getId(), created.getRowVersion());

    // 软删除后 getByIdAndOwner 应抛 not found
    assertThatThrownBy(() -> service.getByIdAndOwner(created.getId()))
        .isInstanceOf(KnowledgeBaseNotFoundException.class);
  }

  @Test
  @DisplayName("should reject delete while the KnowledgeBase still owns active documents")
  void shouldRejectDeleteWhenHasActiveDocuments() {
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("InUse KB " + System.nanoTime());
    KnowledgeBase kb = service.create(req);

    KnowledgeDocument doc = new KnowledgeDocument();
    doc.setOwnerId(kb.getOwnerId());
    doc.setKbId(kb.getId());
    doc.setSourceType("MANUAL_NOTE");
    doc.setTitle("owned note");
    doc.setContent("body");
    doc.setContentVersion(1);
    doc.setIndexStatus("PENDING");
    doc.setDeleted(false);
    doc.setRowVersion(0);
    documentMapper.insert(doc);

    // 该 KB 下仍有未软删文档 → 阻止删除
    assertThatThrownBy(() -> service.softDelete(kb.getId(), kb.getRowVersion()))
        .isInstanceOf(KnowledgeBaseInUseException.class);

    // 软删文档后 KB 可删除
    documentMapper.update(
        null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeDocument>()
            .eq(KnowledgeDocument::getId, doc.getId())
            .set(KnowledgeDocument::getDeleted, true));
    service.softDelete(kb.getId(), kb.getRowVersion());
  }

  @Test
  @DisplayName("should disable and enable KnowledgeBase")
  void shouldDisableAndEnable() {
    CreateKnowledgeBaseRequest req = new CreateKnowledgeBaseRequest();
    req.setName("Toggle Test KB");
    KnowledgeBase created = service.create(req);
    assertThat(created.getEnabled()).isTrue();

    int disabledVersion = service.disable(created.getId(), created.getRowVersion());
    KnowledgeBase disabled = service.getByIdAndOwner(created.getId());
    assertThat(disabled.getEnabled()).isFalse();
    assertThat(disabled.getRowVersion()).isEqualTo(disabledVersion);

    service.enable(created.getId(), disabledVersion);
    KnowledgeBase enabled = service.getByIdAndOwner(created.getId());
    assertThat(enabled.getEnabled()).isTrue();
  }

  @Test
  @DisplayName("should isolate data across owners")
  void shouldIsolateAcrossOwners() {
    long currentOwner = currentOwnerProvider.getCurrentOwnerId();

    // 直接创建一个 second owner，再通过 Mapper 创建其知识库。
    AppUser secondOwner = new AppUser();
    secondOwner.setName("Second Owner");
    secondOwner.setStatus("ACTIVE");
    appUserMapper.insert(secondOwner);
    assertThat(secondOwner.getId()).isNotNull();

    KnowledgeBase other = new KnowledgeBase();
    other.setOwnerId(secondOwner.getId());
    other.setDisplayName("Other Owner KB");
    other.setNormalizedName("other owner kb");
    other.setEnabled(true);
    other.setDeleted(false);
    other.setRowVersion(0);
    knowledgeBaseMapper.insert(other);

    // 当前 owner 必须看不到、取不到、改不了、删不了另一个 owner 的行。
    List<KnowledgeBase> list = service.listForOwner();
    assertThat(list).noneMatch(kb -> kb.getId().equals(other.getId()));

    assertThatThrownBy(() -> service.getByIdAndOwner(other.getId()))
        .isInstanceOf(KnowledgeBaseNotFoundException.class);
    assertThatThrownBy(() -> service.update(other.getId(), new UpdateKnowledgeBaseRequest()))
        .isInstanceOf(KnowledgeBaseNotFoundException.class);
    assertThatThrownBy(() -> service.disable(other.getId(), 0))
        .isInstanceOf(KnowledgeBaseNotFoundException.class);
    assertThatThrownBy(() -> service.softDelete(other.getId(), 0))
        .isInstanceOf(KnowledgeBaseNotFoundException.class);

    // 不同 owner 下相同的规范化名称是允许的。
    CreateKnowledgeBaseRequest sameName = new CreateKnowledgeBaseRequest();
    sameName.setName("Other Owner KB");
    KnowledgeBase own = service.create(sameName);
    assertThat(own.getOwnerId()).isEqualTo(currentOwner);
  }

  @Test
  @DisplayName("should permit repeated deletion and recreation of the same normalized name")
  void shouldPermitRepeatedDeleteAndRecreate() {
    CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
    request.setName("Repeatable Name");

    KnowledgeBase first = service.create(request);
    service.softDelete(first.getId(), first.getRowVersion());
    KnowledgeBase second = service.create(request);
    service.softDelete(second.getId(), second.getRowVersion());
    KnowledgeBase third = service.create(request);

    assertThat(third.getId()).isNotIn(first.getId(), second.getId());
  }
}
