package knowflow.sanjin.modules.conversation.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatResponse;
import tools.jackson.databind.ObjectMapper;

/** ConversationTitleService 单元测试：占位守卫、幂等、Utility 失败回退。 */
class ConversationTitleServiceTest {

  private static final long CONVERSATION_ID = 11L;
  private static final long OWNER_ID = 1L;

  private ConversationService conversationService;
  private TaskSubmissionService taskSubmissionService;
  private ModelConfigService modelConfigService;
  private ModelClientFactory modelClientFactory;
  private ConversationTitleService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    conversationService = mock(ConversationService.class);
    taskSubmissionService = mock(TaskSubmissionService.class);
    modelConfigService = mock(ModelConfigService.class);
    modelClientFactory = mock(ModelClientFactory.class);
    objectMapper = new ObjectMapper();
    CurrentOwnerProvider ownerProvider = mock(CurrentOwnerProvider.class);
    when(ownerProvider.getCurrentOwnerId()).thenReturn(OWNER_ID);
    service =
        new ConversationTitleService(
            conversationService,
            taskSubmissionService,
            modelConfigService,
            modelClientFactory,
            ownerProvider,
            objectMapper);
  }

  private ModelConfigRevision revision() {
    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setId(7L);
    return rev;
  }

  private Conversation conversation(String title) {
    Conversation c = new Conversation();
    c.setId(CONVERSATION_ID);
    c.setOwnerId(OWNER_ID);
    c.setTitle(title);
    return c;
  }

  private ChatMessage userMsg(long id, String content) {
    ChatMessage m = new ChatMessage();
    m.setId(id);
    m.setConversationId(CONVERSATION_ID);
    m.setOwnerId(OWNER_ID);
    m.setRole(ChatMessage.ROLE_USER);
    m.setContent(content);
    return m;
  }

  private ChatMessage assistantMsg(long id, long replyTo, String content) {
    ChatMessage m = new ChatMessage();
    m.setId(id);
    m.setConversationId(CONVERSATION_ID);
    m.setOwnerId(OWNER_ID);
    m.setRole(ChatMessage.ROLE_ASSISTANT);
    m.setContent(content);
    m.setReplyToMessageId(replyTo);
    m.setGenerationStatus(ChatMessage.COMPLETED);
    m.setIsActive(true);
    return m;
  }

  private ProcessingTask task(String payload) {
    ProcessingTask t = new ProcessingTask();
    t.setId(99L);
    t.setTaskType(ProcessingConstants.TASK_TYPE_CONVERSATION_TITLE);
    t.setPayload(payload);
    return t;
  }

  @Test
  @DisplayName("should not submit title task when conversation title is not placeholder")
  void shouldNotSubmitWhenTitleIsNotPlaceholder() {
    when(conversationService.getByIdAndOwner(CONVERSATION_ID)).thenReturn(conversation("已手动改名"));

    service.ensureTitleTask(CONVERSATION_ID);

    verify(taskSubmissionService, never())
        .submit(anyString(), anyString(), any(), anyLong(), anyString(), anyInt(), anyString());
  }

  @Test
  @DisplayName("should submit title task when title is placeholder, with conversationId payload")
  void shouldSubmitWhenTitleIsPlaceholder() throws Exception {
    when(conversationService.getByIdAndOwner(CONVERSATION_ID))
        .thenReturn(conversation(ConversationService.TITLE_PLACEHOLDER));

    service.ensureTitleTask(CONVERSATION_ID);

    ArgumentCaptor<String> taskType = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> businessKey = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Long> businessId = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> ownerId = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> maxRetries = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<String> queueBase = ArgumentCaptor.forClass(String.class);
    verify(taskSubmissionService)
        .submit(
            taskType.capture(),
            businessKey.capture(),
            businessId.capture(),
            ownerId.capture(),
            payload.capture(),
            maxRetries.capture(),
            queueBase.capture());
    assertThat(taskType.getValue()).isEqualTo(ProcessingConstants.TASK_TYPE_CONVERSATION_TITLE);
    assertThat(businessKey.getValue()).isEqualTo(String.valueOf(CONVERSATION_ID));
    assertThat(businessId.getValue()).isEqualTo(CONVERSATION_ID);
    assertThat(ownerId.getValue()).isEqualTo(OWNER_ID);
    assertThat(queueBase.getValue()).isEqualTo(ConversationTitleService.WORK_QUEUE_BASE);
    assertThat(maxRetries.getValue()).isEqualTo(2);
    ConversationTitleService.TitleTaskPayload parsed =
        objectMapper.readValue(payload.getValue(), ConversationTitleService.TitleTaskPayload.class);
    assertThat(parsed.conversationId()).isEqualTo(CONVERSATION_ID);
  }

  @Test
  @DisplayName("should not override title when title is no longer placeholder at execute time")
  void shouldNotOverrideWhenAlreadyGenerated() throws Exception {
    when(conversationService.getByIdAndOwner(CONVERSATION_ID)).thenReturn(conversation("已被 AI 生成"));

    service.execute(task("{\"conversationId\":" + CONVERSATION_ID + "}"));

    verify(conversationService, never()).setTitleIfPlaceholder(anyLong(), anyString());
  }

  @Test
  @DisplayName("should write AI generated title via setTitleIfPlaceholder on success")
  void shouldWriteGeneratedTitle() throws Exception {
    when(conversationService.getByIdAndOwner(CONVERSATION_ID))
        .thenReturn(conversation(ConversationService.TITLE_PLACEHOLDER));
    when(conversationService.loadRecentContext(CONVERSATION_ID, 1))
        .thenReturn(List.of(userMsg(1L, "什么是索引？"), assistantMsg(2L, 1L, "索引是…")));
    when(modelConfigService.resolveUtilityRevisionForRouting()).thenReturn(revision());
    when(modelClientFactory.extractText(any())).thenReturn("索引机制详解");
    when(modelClientFactory.callWithTotalTimeout(any(), anyLong()))
        .thenReturn(mock(ChatResponse.class));

    service.execute(task("{\"conversationId\":" + CONVERSATION_ID + "}"));

    verify(conversationService).setTitleIfPlaceholder(CONVERSATION_ID, "索引机制详解");
  }

  @Test
  @DisplayName("should fallback to first user message truncation when utility model fails")
  void shouldFallbackWhenUtilityFails() throws Exception {
    when(conversationService.getByIdAndOwner(CONVERSATION_ID))
        .thenReturn(conversation(ConversationService.TITLE_PLACEHOLDER));
    when(conversationService.loadRecentContext(CONVERSATION_ID, 1))
        .thenReturn(
            List.of(userMsg(1L, "这段对话是一段非常长的用户问题\n用于测试标题回退截断"), assistantMsg(2L, 1L, "回答")));
    when(modelConfigService.resolveUtilityRevisionForRouting())
        .thenThrow(new RuntimeException("utility 未配置"));

    service.execute(task("{\"conversationId\":" + CONVERSATION_ID + "}"));

    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    verify(conversationService).setTitleIfPlaceholder(eq(CONVERSATION_ID), title.capture());
    assertThat(title.getValue()).doesNotContain("\n");
    assertThat(title.getValue().length()).isLessThanOrEqualTo(30);
    assertThat(title.getValue()).startsWith("这段对话");
  }

  @Test
  @DisplayName("should fallback when utility call throws timeout")
  void shouldFallbackWhenUtilityTimesOut() throws Exception {
    when(conversationService.getByIdAndOwner(CONVERSATION_ID))
        .thenReturn(conversation(ConversationService.TITLE_PLACEHOLDER));
    when(conversationService.loadRecentContext(CONVERSATION_ID, 1))
        .thenReturn(List.of(userMsg(1L, "标题回退测试问题"), assistantMsg(2L, 1L, "回答")));
    when(modelConfigService.resolveUtilityRevisionForRouting()).thenReturn(revision());
    when(modelClientFactory.callWithTotalTimeout(any(), anyLong()))
        .thenThrow(new RuntimeException("timeout"));

    service.execute(task("{\"conversationId\":" + CONVERSATION_ID + "}"));

    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    verify(conversationService).setTitleIfPlaceholder(eq(CONVERSATION_ID), title.capture());
    assertThat(title.getValue()).isEqualTo("标题回退测试问题");
  }
}
