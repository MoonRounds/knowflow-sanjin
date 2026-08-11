package knowflow.sanjin.modules.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.config.ExtractionProperties;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.exception.ExtractionInputOverBudgetException;
import knowflow.sanjin.modules.extraction.exception.ExtractionNoCompletedMessagesException;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeExtractionTaskMapper;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ExtractionService 单元测试：cutoff 快照、预算拒绝、幂等去重、任务提交。 */
class ExtractionServiceTest {

  private static final long OWNER_ID = 1L;

  private ExtractionProperties properties;
  private CurrentOwnerProvider ownerProvider;
  private ConversationService conversationService;
  private ModelConfigService modelConfigService;
  private TaskSubmissionService taskSubmissionService;
  private KnowledgeExtractionTaskMapper taskMapper;
  private ExtractionService service;

  @BeforeEach
  void setUp() {
    properties = new ExtractionProperties();
    ownerProvider = mock(CurrentOwnerProvider.class);
    when(ownerProvider.getCurrentOwnerId()).thenReturn(OWNER_ID);
    conversationService = mock(ConversationService.class);
    modelConfigService = mock(ModelConfigService.class);
    taskSubmissionService = mock(TaskSubmissionService.class);
    taskMapper = mock(KnowledgeExtractionTaskMapper.class);
    service =
        new ExtractionService(
            ownerProvider,
            conversationService,
            modelConfigService,
            taskSubmissionService,
            taskMapper,
            properties);

    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setId(7L);
    when(modelConfigService.resolveUtilityRevisionForRouting()).thenReturn(rev);
  }

  private ChatMessage msg(long id, String role, String content) {
    ChatMessage m = new ChatMessage();
    m.setId(id);
    m.setRole(role);
    m.setContent(content);
    m.setGenerationStatus(ChatMessage.COMPLETED);
    m.setIsActive(true);
    m.setReplyToMessageId(role.equals(ChatMessage.ROLE_ASSISTANT) ? id - 1 : null);
    return m;
  }

  @Test
  @DisplayName("should trigger and create snapshot with cutoff + budget")
  void shouldTriggerAndCreateSnapshot() {
    ChatMessage last = msg(10L, ChatMessage.ROLE_ASSISTANT, "回答");
    when(conversationService.lastMessageId(any(), anyLong())).thenReturn(last.getId());
    when(conversationService.loadAllTurnsUpTo(any(), anyLong(), any()))
        .thenReturn(List.of(msg(9L, ChatMessage.ROLE_USER, "问题"), last));
    when(taskMapper.selectOne(any())).thenReturn(null);
    ProcessingTask task = new ProcessingTask();
    task.setId(100L);
    when(taskSubmissionService.submit(
            anyString(), anyString(), any(), anyLong(), any(), anyInt(), any()))
        .thenReturn(task);

    KnowledgeExtractionTask snapshot = service.trigger(1L);

    assertThat(snapshot.getCutoffMessageId()).isEqualTo(10L);
    assertThat(snapshot.getConversationId()).isEqualTo(1L);
    assertThat(snapshot.getUtilityRevisionId()).isEqualTo(7L);
    assertThat(snapshot.getProcessingTaskId()).isEqualTo(100L);
    assertThat(snapshot.getInputCharCount()).isEqualTo(4);
    ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
    verify(taskSubmissionService)
        .submit(anyString(), keyCap.capture(), any(), anyLong(), any(), anyInt(), any());
    assertThat(keyCap.getValue()).startsWith(ExtractionConstants.BUSINESS_KEY_PREFIX);
    assertThat(keyCap.getValue()).contains("10");
    // F2: 触发时把 cutoff 传给 Turn 加载，限定输入范围
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Long> cutoffCap = ArgumentCaptor.forClass(Long.class);
    verify(conversationService).loadAllTurnsUpTo(any(), anyLong(), cutoffCap.capture());
    assertThat(cutoffCap.getValue()).isEqualTo(10L);
  }

  @Test
  @DisplayName("should reject with no-completed-messages when conversation has no turns")
  void shouldRejectWhenNoCompletedMessages() {
    when(conversationService.lastMessageId(any(), anyLong())).thenReturn(10L);
    when(conversationService.loadAllTurnsUpTo(any(), anyLong(), any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.trigger(1L))
        .isInstanceOf(ExtractionNoCompletedMessagesException.class);
    verify(taskSubmissionService, never())
        .submit(any(), any(), any(), anyLong(), any(), anyInt(), any());
  }

  @Test
  @DisplayName("should reject over budget without creating a task")
  void shouldRejectOverBudget() {
    properties.setInputCharBudget(5);
    when(conversationService.lastMessageId(any(), anyLong())).thenReturn(10L);
    when(conversationService.loadAllTurnsUpTo(any(), anyLong(), any()))
        .thenReturn(
            List.of(
                msg(9L, ChatMessage.ROLE_USER, "12345"),
                msg(10L, ChatMessage.ROLE_ASSISTANT, "x")));

    assertThatThrownBy(() -> service.trigger(1L))
        .isInstanceOf(ExtractionInputOverBudgetException.class);
    verify(taskSubmissionService, never())
        .submit(any(), any(), any(), anyLong(), any(), anyInt(), any());
  }

  @Test
  @DisplayName("should return existing snapshot when same dedup key already exists")
  void shouldReturnExistingOnDuplicate() {
    when(conversationService.lastMessageId(any(), anyLong())).thenReturn(10L);
    when(conversationService.loadAllTurnsUpTo(any(), anyLong(), any()))
        .thenReturn(
            List.of(
                msg(9L, ChatMessage.ROLE_USER, "问题"), msg(10L, ChatMessage.ROLE_ASSISTANT, "回答")));
    KnowledgeExtractionTask existing = new KnowledgeExtractionTask();
    existing.setId(5L);
    existing.setCutoffMessageId(10L);
    when(taskMapper.selectOne(any())).thenReturn(existing);

    KnowledgeExtractionTask snapshot = service.trigger(1L);

    assertThat(snapshot.getId()).isEqualTo(5L);
    verify(taskSubmissionService, never())
        .submit(any(), any(), any(), anyLong(), any(), anyInt(), any());
  }
}
