package knowflow.sanjin.modules.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.exception.CandidateEmptyDraftException;
import knowflow.sanjin.modules.extraction.exception.CandidateInvalidStateException;
import knowflow.sanjin.modules.extraction.exception.CandidateNoKnowledgeBaseException;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentTagMapper;
import knowflow.sanjin.modules.knowledge.mapper.TagMapper;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CandidateConfirmService 单元测试：确认幂等、草稿校验、拒绝不建 Item。 */
class CandidateConfirmServiceTest {

  private static final long OWNER_ID = 1L;

  private KnowledgeCandidateMapper candidateMapper;
  private KnowledgeDocumentMapper itemMapper;
  private KnowledgeBaseService knowledgeBaseService;
  private TaskSubmissionService taskSubmissionService;
  private CandidateConfirmService service;
  private final java.util.concurrent.atomic.AtomicReference<KnowledgeCandidate> state =
      new java.util.concurrent.atomic.AtomicReference<>();

  @BeforeEach
  void setUp() {
    // 纯单测环境需初始化 MyBatis-Plus 元数据，否则 LambdaUpdateWrapper 解析列名失败
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "confirm-test"),
        KnowledgeCandidate.class);
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "confirm-test"),
        KnowledgeDocument.class);

    candidateMapper = mock(KnowledgeCandidateMapper.class);
    itemMapper = mock(KnowledgeDocumentMapper.class);
    knowledgeBaseService = mock(KnowledgeBaseService.class);
    taskSubmissionService = mock(TaskSubmissionService.class);
    CurrentOwnerProvider ownerProvider = mock(CurrentOwnerProvider.class);
    when(ownerProvider.getCurrentOwnerId()).thenReturn(OWNER_ID);
    service =
        new CandidateConfirmService(
            ownerProvider,
            candidateMapper,
            itemMapper,
            mock(KnowledgeDocumentTagMapper.class),
            mock(TagMapper.class),
            mock(KnowledgeBaseMapper.class),
            knowledgeBaseService,
            taskSubmissionService);

    KnowledgeCandidate candidate = new KnowledgeCandidate();
    candidate.setId(1L);
    candidate.setOwnerId(OWNER_ID);
    candidate.setStatus(ExtractionConstants.CANDIDATE_PENDING);
    candidate.setDraftTitle("标题");
    candidate.setDraftSummary("摘要");
    candidate.setDraftContent("正文内容");
    candidate.setDraftKnowledgeBaseId("1");
    candidate.setDraftTags("tag1,tag2");
    candidate.setRowVersion(0);
    state.set(candidate);
    when(candidateMapper.selectOne(any())).thenAnswer(inv -> state.get());
  }

  @Test
  @DisplayName("should return already-confirmed candidate idempotently")
  void shouldReturnAlreadyConfirmedIdempotently() {
    state.get().setStatus(ExtractionConstants.CANDIDATE_CONFIRMED);

    KnowledgeCandidate result = service.confirm(1L);

    assertThat(result.getStatus()).isEqualTo(ExtractionConstants.CANDIDATE_CONFIRMED);
  }

  @Test
  @DisplayName("should reject confirm when candidate has no KnowledgeBase in draft")
  void shouldRejectConfirmWithoutKb() {
    state.get().setDraftKnowledgeBaseId("");

    assertThatThrownBy(() -> service.confirm(1L))
        .isInstanceOf(CandidateNoKnowledgeBaseException.class);
  }

  @Test
  @DisplayName("should reject confirm when draft content is empty")
  void shouldRejectConfirmEmptyContent() {
    state.get().setDraftContent("");

    assertThatThrownBy(() -> service.confirm(1L)).isInstanceOf(CandidateEmptyDraftException.class);
  }

  @Test
  @DisplayName("should reject confirm when candidate is REJECTED")
  void shouldRejectConfirmWhenRejected() {
    state.get().setStatus(ExtractionConstants.CANDIDATE_REJECTED);

    assertThatThrownBy(() -> service.confirm(1L))
        .isInstanceOf(CandidateInvalidStateException.class);
  }

  @Test
  @DisplayName("should confirm and create one item")
  void shouldConfirmAndCreateItem() {
    when(itemMapper.insert(any(KnowledgeDocument.class)))
        .thenAnswer(
            inv -> {
              KnowledgeDocument doc = inv.getArgument(0);
              doc.setId(10L);
              return 1;
            });
    when(candidateMapper.update(any(), any()))
        .thenAnswer(
            inv -> {
              state.get().setStatus(ExtractionConstants.CANDIDATE_CONFIRMED);
              return 1;
            });

    KnowledgeCandidate result = service.confirm(1L);

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(ExtractionConstants.CANDIDATE_CONFIRMED);

    // 创建的唯一 Document 单归属到 draftKnowledgeBaseId 对应的 KB
    org.mockito.ArgumentCaptor<KnowledgeDocument> captor =
        org.mockito.ArgumentCaptor.forClass(KnowledgeDocument.class);
    verify(itemMapper).insert(captor.capture());
    assertThat(captor.getValue().getKbId()).isEqualTo(1L);
    assertThat(captor.getValue().getSourceType())
        .isEqualTo(ExtractionConstants.SOURCE_AI_CONVERSATION);
    assertThat(captor.getValue().getCandidateId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("should be idempotent on concurrent double-click via unique constraint")
  void shouldBeIdempotentOnConcurrentInsertConflict() {
    // 并发双击：第二个事务 insert 触发 candidate_id 唯一约束 → 捕获 DuplicateKeyException 返回已确认候选
    when(itemMapper.insert(any(KnowledgeDocument.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_kitem_candidate"));
    state.get().setStatus(ExtractionConstants.CANDIDATE_CONFIRMED);

    KnowledgeCandidate result = service.confirm(1L);

    assertThat(result.getStatus()).isEqualTo(ExtractionConstants.CANDIDATE_CONFIRMED);
  }

  @Test
  @DisplayName(
      "F9: should roll back when candidate status update conflicts with a concurrent reject")
  void shouldRollbackWhenStatusUpdateConflicts() {
    // 并发 reject 抢先：确认事务里候选状态已非 PENDING，update 影响 0 行 → 抛 InvalidState，整个事务回滚（Item 不得残留）
    when(itemMapper.insert(any(KnowledgeDocument.class))).thenReturn(1);
    when(candidateMapper.update(any(), any())).thenReturn(0);

    assertThatThrownBy(() -> service.confirm(1L))
        .isInstanceOf(CandidateInvalidStateException.class);
  }
}
