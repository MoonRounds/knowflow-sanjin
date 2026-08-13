package knowflow.sanjin.modules.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.concurrent.atomic.AtomicReference;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.exception.CandidateInvalidStateException;
import knowflow.sanjin.modules.extraction.exception.CandidateNotFoundException;
import knowflow.sanjin.modules.extraction.exception.CandidateVersionConflictException;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CandidateService 单元测试：状态迁移与乐观锁保护。 */
class CandidateServiceTest {

  private static final long OWNER_ID = 1L;

  private KnowledgeCandidateMapper candidateMapper;
  private CandidateService service;
  private final AtomicReference<KnowledgeCandidate> state = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    // 纯单测环境需初始化 MyBatis-Plus 元数据，否则 LambdaUpdateWrapper 解析列名失败
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "candidate-test"),
        KnowledgeCandidate.class);

    candidateMapper = mock(KnowledgeCandidateMapper.class);
    CurrentOwnerProvider ownerProvider = mock(CurrentOwnerProvider.class);
    when(ownerProvider.getCurrentOwnerId()).thenReturn(OWNER_ID);
    service = new CandidateService(ownerProvider, candidateMapper);

    KnowledgeCandidate candidate = new KnowledgeCandidate();
    candidate.setId(1L);
    candidate.setOwnerId(OWNER_ID);
    candidate.setStatus(ExtractionConstants.CANDIDATE_PENDING);
    candidate.setRowVersion(3);
    state.set(candidate);
    // selectOne 返回可变状态：update 成功后由测试更新 state，模拟真实的读后写
    when(candidateMapper.selectOne(any())).thenAnswer(inv -> state.get());
  }

  @Test
  @DisplayName("should throw not-found when candidate belongs to another owner")
  void shouldThrowNotFoundForOtherOwner() {
    when(candidateMapper.selectOne(any())).thenReturn(null);
    assertThatThrownBy(() -> service.getByIdAndOwner(1L))
        .isInstanceOf(CandidateNotFoundException.class);
  }

  @Test
  @DisplayName("should reject a PENDING candidate")
  void shouldRejectPending() {
    when(candidateMapper.update(any(), any()))
        .thenAnswer(
            inv -> {
              state.get().setStatus(ExtractionConstants.CANDIDATE_REJECTED);
              state.get().setRowVersion(4);
              return 1;
            });

    KnowledgeCandidate result = service.reject(1L, 3);

    assertThat(result.getStatus()).isEqualTo(ExtractionConstants.CANDIDATE_REJECTED);
  }

  @Test
  @DisplayName("should reject with wrong rowVersion as version conflict")
  void shouldRejectWithWrongVersion() {
    when(candidateMapper.update(any(), any())).thenReturn(0);

    assertThatThrownBy(() -> service.reject(1L, 999))
        .isInstanceOf(CandidateVersionConflictException.class);
  }

  @Test
  @DisplayName("should restore a REJECTED candidate back to PENDING")
  void shouldRestoreRejected() {
    state.get().setStatus(ExtractionConstants.CANDIDATE_REJECTED);
    when(candidateMapper.update(any(), any()))
        .thenAnswer(
            inv -> {
              state.get().setStatus(ExtractionConstants.CANDIDATE_PENDING);
              state.get().setRejectedAt(null);
              state.get().setRowVersion(4);
              return 1;
            });

    KnowledgeCandidate result = service.restore(1L, 3);

    assertThat(result.getStatus()).isEqualTo(ExtractionConstants.CANDIDATE_PENDING);
  }

  @Test
  @DisplayName("should reject update-draft with wrong rowVersion as version conflict")
  void shouldRejectDraftWithWrongVersion() {
    knowflow.sanjin.modules.extraction.dto.UpdateCandidateDraftRequest req =
        new knowflow.sanjin.modules.extraction.dto.UpdateCandidateDraftRequest();
    req.setTitle("t");
    req.setContent("c");
    req.setKnowledgeBaseId("1");
    req.setRowVersion(999);

    assertThatThrownBy(() -> service.updateDraft(1L, req))
        .isInstanceOf(CandidateVersionConflictException.class);
  }

  @Test
  @DisplayName("should not allow editing a CONFIRMED candidate")
  void shouldNotEditConfirmed() {
    state.get().setStatus(ExtractionConstants.CANDIDATE_CONFIRMED);
    knowflow.sanjin.modules.extraction.dto.UpdateCandidateDraftRequest req =
        new knowflow.sanjin.modules.extraction.dto.UpdateCandidateDraftRequest();
    req.setTitle("t");
    req.setContent("c");
    req.setKnowledgeBaseId("1");
    req.setRowVersion(3);

    assertThatThrownBy(() -> service.updateDraft(1L, req))
        .isInstanceOf(CandidateInvalidStateException.class);
    verify(candidateMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("should not allow rejecting a CONFIRMED candidate")
  void shouldNotRejectConfirmed() {
    state.get().setStatus(ExtractionConstants.CANDIDATE_CONFIRMED);
    when(candidateMapper.update(any(), any())).thenReturn(0);

    assertThatThrownBy(() -> service.reject(1L, 3))
        .isInstanceOf(CandidateInvalidStateException.class);
    verify(candidateMapper, times(0)).update(any(), any());
  }
}
