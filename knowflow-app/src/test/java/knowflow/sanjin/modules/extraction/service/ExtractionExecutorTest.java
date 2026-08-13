package knowflow.sanjin.modules.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.extraction.config.ExtractionProperties;
import knowflow.sanjin.modules.extraction.dto.ExtractionResult;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeExtractionTaskMapper;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExtractionExecutor 输出校验单元测试：数量上限、必填、KB 目录归属、重复 id、tags 上限。 */
class ExtractionExecutorTest {

  private static final long OWNER_ID = 1L;

  private ExtractionProperties properties;
  private ExtractionExecutor executor;
  private KnowledgeBaseMapper kbMapper;

  @BeforeEach
  void setUp() {
    properties = new ExtractionProperties();
    CurrentOwnerProvider ownerProvider = mock(CurrentOwnerProvider.class);
    when(ownerProvider.getCurrentOwnerId()).thenReturn(OWNER_ID);
    kbMapper = mock(KnowledgeBaseMapper.class);
    KnowledgeBase kb = new KnowledgeBase();
    kb.setId(1L);
    kb.setOwnerId(OWNER_ID);
    kb.setEnabled(true);
    kb.setDeleted(false);
    when(kbMapper.selectList(any())).thenReturn(List.of(kb));
    executor =
        new ExtractionExecutor(
            ownerProvider,
            mock(KnowledgeExtractionTaskMapper.class),
            mock(KnowledgeCandidateMapper.class),
            kbMapper,
            mock(ConversationService.class),
            mock(ModelClientFactory.class),
            mock(ModelConfigService.class),
            properties);
  }

  private ExtractionResult.Candidate candidate(String title, String content, String kbId) {
    ExtractionResult.Candidate c = new ExtractionResult.Candidate();
    c.setTitle(title);
    c.setContent(content);
    c.setKnowledgeBaseId(kbId);
    return c;
  }

  private static ExtractionResult result(ExtractionResult.Candidate... candidates) {
    ExtractionResult r = new ExtractionResult();
    r.setCandidates(java.util.Arrays.asList(candidates));
    return r;
  }

  @Test
  @DisplayName("should accept zero candidates as valid (empty output is a success result)")
  void shouldAcceptZeroCandidates() {
    assertThat(executor.isValid(result())).isTrue();
  }

  @Test
  @DisplayName("should accept a single valid candidate with catalog KB")
  void shouldAcceptSingleValidCandidate() {
    assertThat(executor.isValid(result(candidate("标题", "内容", "1")))).isTrue();
  }

  @Test
  @DisplayName("should reject when candidate count exceeds max")
  void shouldRejectTooManyCandidates() {
    properties.setMaxCandidates(2);
    assertThat(
            executor.isValid(
                result(
                    candidate("a", "c1", "1"),
                    candidate("b", "c2", "1"),
                    candidate("c", "c3", "1"))))
        .isFalse();
  }

  @Test
  @DisplayName("should reject when title or content is blank")
  void shouldRejectBlankTitleOrContent() {
    assertThat(executor.isValid(result(candidate("", "内容", "1")))).isFalse();
    assertThat(executor.isValid(result(candidate("标题", "", "1")))).isFalse();
  }

  @Test
  @DisplayName("should reject unknown KB id not in the catalog")
  void shouldRejectUnknownKbId() {
    assertThat(executor.isValid(result(candidate("标题", "内容", "999")))).isFalse();
  }

  @Test
  @DisplayName("should reject non-numeric KB id")
  void shouldRejectNonNumericKbId() {
    assertThat(executor.isValid(result(candidate("标题", "内容", "not-a-number")))).isFalse();
  }

  @Test
  @DisplayName("should accept a candidate with a blank KB id (AI did not recommend a KB)")
  void shouldAcceptBlankKbId() {
    assertThat(executor.isValid(result(candidate("标题", "内容", null)))).isTrue();
    assertThat(executor.isValid(result(candidate("标题", "内容", "  ")))).isTrue();
  }

  @Test
  @DisplayName("should reject null result (unparseable output)")
  void shouldRejectNullResult() {
    assertThat(executor.isValid(null)).isFalse();
  }
}
