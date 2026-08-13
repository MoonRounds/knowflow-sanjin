package knowflow.sanjin.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocumentTag;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentTagMapper;
import knowflow.sanjin.modules.knowledge.mapper.TagMapper;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.owner.entity.AppUser;
import knowflow.sanjin.modules.owner.mapper.AppUserMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 文档列表查询（P2）：按库 + sourceType/indexStatus/tag 过滤 + 分页；documentCount 与标签列表。 */
@SpringBootTest
@DisplayName("KnowledgeDocument Query Integration Tests (P2)")
class KnowledgeDocumentQueryIT extends MySQLTestBase {

  @Autowired private KnowledgeDocumentService service;

  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Autowired private KnowledgeDocumentMapper documentMapper;

  @Autowired private KnowledgeDocumentTagMapper documentTagMapper;

  @Autowired private TagMapper tagMapper;

  @Autowired private AppUserMapper appUserMapper;

  @Autowired private CurrentOwnerProvider currentOwnerProvider;

  private KnowledgeBase kb;

  @BeforeEach
  void setUp() {
    CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
    request.setName("Query KB " + UUID.randomUUID());
    kb = knowledgeBaseService.create(request);
  }

  @Test
  @DisplayName("should page documents of the owning knowledge base only")
  void shouldPageByKnowledgeBase() {
    KnowledgeBase other = createKb();
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "a", false);
    insertDoc(kb.getId(), "UPLOAD_FILE", "INDEXED", "b", false);
    insertDoc(other.getId(), "MANUAL_NOTE", "PENDING", "c", false);

    Page<KnowledgeDocument> page = service.pageForOwner(kb.getId(), null, null, null, 1, 20);

    assertThat(page.getTotal()).isEqualTo(2);
    assertThat(page.getRecords())
        .extracting(KnowledgeDocument::getTitle)
        .containsExactlyInAnyOrder("a", "b");
    assertThat(page.getRecords()).allMatch(d -> d.getKbId().equals(kb.getId()));
  }

  @Test
  @DisplayName("should combine sourceType and indexStatus filters")
  void shouldFilterBySourceTypeAndIndexStatus() {
    insertDoc(kb.getId(), "MANUAL_NOTE", "INDEXED", "m1", false);
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "m2", false);
    insertDoc(kb.getId(), "UPLOAD_FILE", "INDEXED", "u1", false);
    insertDoc(kb.getId(), "UPLOAD_FILE", "FAILED", "u2", false);

    Page<KnowledgeDocument> page =
        service.pageForOwner(kb.getId(), "UPLOAD_FILE", null, "INDEXED", 1, 20);

    assertThat(page.getTotal()).isEqualTo(1);
    assertThat(page.getRecords()).extracting(KnowledgeDocument::getTitle).containsExactly("u1");
  }

  @Test
  @DisplayName("should filter documents by exact tag name")
  void shouldFilterByTag() {
    KnowledgeDocument tagged = insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "tagged", false);
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "plain", false);
    Long tagId = insertTag("java");
    linkTag(tagged.getId(), tagId);

    Page<KnowledgeDocument> page = service.pageForOwner(kb.getId(), null, "java", null, 1, 20);

    assertThat(page.getTotal()).isEqualTo(1);
    assertThat(page.getRecords()).extracting(KnowledgeDocument::getTitle).containsExactly("tagged");
  }

  @Test
  @DisplayName("should return empty page when tag does not exist")
  void shouldReturnEmptyWhenTagMissing() {
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "a", false);

    Page<KnowledgeDocument> page =
        service.pageForOwner(kb.getId(), null, "missing-tag", null, 1, 20);

    assertThat(page.getTotal()).isZero();
    assertThat(page.getRecords()).isEmpty();
  }

  @Test
  @DisplayName("should filter by tag matching either display name or normalized name")
  void shouldFilterByTagNameOrNormalizedName() {
    // 防御未来展示名与规范化名分化：GET /tags 返回 name，过滤按 name 或 normalized_name 任一匹配
    KnowledgeDocument tagged = insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "tagged", false);
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "plain", false);
    Tag tag = new Tag();
    tag.setOwnerId(currentOwnerProvider.getCurrentOwnerId());
    tag.setName("Java Notes");
    tag.setNormalizedName("java-notes");
    tag.setDeleted(false);
    tagMapper.insert(tag);
    linkTag(tagged.getId(), tag.getId());

    Page<KnowledgeDocument> byName =
        service.pageForOwner(kb.getId(), null, "Java Notes", null, 1, 20);
    Page<KnowledgeDocument> byNormalized =
        service.pageForOwner(kb.getId(), null, "java-notes", null, 1, 20);

    assertThat(byName.getTotal()).isEqualTo(1);
    assertThat(byName.getRecords())
        .extracting(KnowledgeDocument::getTitle)
        .containsExactly("tagged");
    assertThat(byNormalized.getTotal()).isEqualTo(1);
    assertThat(byNormalized.getRecords())
        .extracting(KnowledgeDocument::getTitle)
        .containsExactly("tagged");
  }

  @Test
  @DisplayName("should paginate and exclude soft-deleted documents")
  void shouldPaginateAndExcludeDeleted() {
    for (int i = 0; i < 5; i++) {
      insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "doc-" + i, false);
    }
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "gone", true);

    Page<KnowledgeDocument> first = service.pageForOwner(kb.getId(), null, null, null, 1, 2);
    Page<KnowledgeDocument> second = service.pageForOwner(kb.getId(), null, null, null, 2, 2);
    Page<KnowledgeDocument> third = service.pageForOwner(kb.getId(), null, null, null, 3, 2);

    assertThat(first.getTotal()).isEqualTo(5);
    assertThat(first.getRecords()).hasSize(2);
    assertThat(second.getRecords()).hasSize(2);
    assertThat(third.getRecords()).hasSize(1);

    List<Long> pageOneIds = first.getRecords().stream().map(KnowledgeDocument::getId).toList();
    List<Long> pageTwoIds = second.getRecords().stream().map(KnowledgeDocument::getId).toList();
    assertThat(pageOneIds).doesNotContainAnyElementsOf(pageTwoIds);
    assertThat(pageTwoIds).doesNotContain(third.getRecords().get(0).getId());
  }

  @Test
  @DisplayName("should count active documents per knowledge base")
  void shouldCountActiveDocumentsByKb() {
    KnowledgeBase other = createKb();
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "x", false);
    insertDoc(kb.getId(), "UPLOAD_FILE", "INDEXED", "y", false);
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "z", true);
    insertDoc(other.getId(), "MANUAL_NOTE", "PENDING", "w", false);

    Map<Long, Long> counts = knowledgeBaseService.countActiveDocumentsByKb();

    assertThat(counts.get(kb.getId())).isEqualTo(2);
    assertThat(counts.get(other.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName("should list owner tags sorted by name")
  void shouldListOwnerTags() {
    // 标签是 owner 级、跨测试共享同一 MySQL：用唯一名断言「包含 + 有序」，不做全量相等
    String tagA = "alpha-" + UUID.randomUUID();
    String tagB = "beta-" + UUID.randomUUID();
    insertTag(tagB);
    insertTag(tagA);

    List<String> names = service.listTagsForOwner().stream().map(Tag::getNormalizedName).toList();

    assertThat(names).contains(tagA, tagB);
    assertThat(names).isSorted();
  }

  @Test
  @DisplayName("should isolate query results across owners")
  void shouldIsolateAcrossOwners() {
    insertDoc(kb.getId(), "MANUAL_NOTE", "PENDING", "mine", false);

    AppUser secondOwner = new AppUser();
    secondOwner.setName("Second Owner");
    secondOwner.setStatus("ACTIVE");
    appUserMapper.insert(secondOwner);

    KnowledgeDocument other = new KnowledgeDocument();
    other.setOwnerId(secondOwner.getId());
    other.setKbId(kb.getId());
    other.setSourceType("MANUAL_NOTE");
    other.setTitle("theirs");
    other.setContent("private");
    other.setContentVersion(1);
    other.setIndexStatus("PENDING");
    other.setDeleted(false);
    other.setRowVersion(0);
    documentMapper.insert(other);

    Page<KnowledgeDocument> page = service.pageForOwner(kb.getId(), null, null, null, 1, 20);

    assertThat(page.getTotal()).isEqualTo(1);
    assertThat(page.getRecords())
        .extracting(KnowledgeDocument::getTitle)
        .containsExactly("mine")
        .doesNotContain("theirs");
  }

  private KnowledgeBase createKb() {
    CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
    request.setName("KB " + UUID.randomUUID());
    return knowledgeBaseService.create(request);
  }

  private KnowledgeDocument insertDoc(
      Long kbId, String sourceType, String indexStatus, String title, boolean deleted) {
    KnowledgeDocument doc = new KnowledgeDocument();
    doc.setOwnerId(currentOwnerProvider.getCurrentOwnerId());
    doc.setKbId(kbId);
    doc.setSourceType(sourceType);
    doc.setTitle(title);
    doc.setContent("content-" + title);
    doc.setContentVersion(1);
    doc.setIndexStatus(indexStatus);
    doc.setDeleted(deleted);
    doc.setRowVersion(0);
    documentMapper.insert(doc);
    return doc;
  }

  private Long insertTag(String name) {
    Tag tag = new Tag();
    tag.setOwnerId(currentOwnerProvider.getCurrentOwnerId());
    tag.setName(name);
    tag.setNormalizedName(name);
    tag.setDeleted(false);
    tagMapper.insert(tag);
    return tag.getId();
  }

  private void linkTag(Long documentId, Long tagId) {
    KnowledgeDocumentTag rel = new KnowledgeDocumentTag();
    rel.setOwnerId(currentOwnerProvider.getCurrentOwnerId());
    rel.setKnowledgeDocumentId(documentId);
    rel.setTagId(tagId);
    rel.setDeleted(false);
    documentTagMapper.insert(rel);
  }
}
