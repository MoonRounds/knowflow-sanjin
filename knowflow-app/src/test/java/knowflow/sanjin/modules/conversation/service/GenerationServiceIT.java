package knowflow.sanjin.modules.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.SendMessageRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * Generation 集成测试：覆盖发送、clientMessageId 幂等、并发生成、模型默认更新与 active slot 认领。 使用 mock ChatModel 避免真实
 * Provider 调用。
 */
@SpringBootTest
@DisplayName("Generation Integration Tests")
class GenerationServiceIT extends MySQLTestBase {

  @Autowired private ConversationService conversationService;
  @Autowired private ModelConfigService modelConfigService;
  @Autowired private GenerationService generationService;
  @MockitoBean private ModelClientFactory modelClientFactory;

  private Conversation conversation;
  private ModelConfig modelConfig;

  @BeforeEach
  void setUp() {
    CreateConversationRequest req = new CreateConversationRequest();
    req.setTitle("generation-it");
    conversation = conversationService.create(req);

    CreateModelConfigRequest mc = new CreateModelConfigRequest();
    mc.setDisplayName("stub");
    mc.setProviderName("stub");
    mc.setBaseUrl("http://127.0.0.1:9999/v1");
    mc.setModelName("stub-model");
    mc.setTemperature(0.7);
    mc.setMaxOutputTokens(2048);
    mc.setApiKey("sk-test-key");
    modelConfig = modelConfigService.create(mc);
  }

  @AfterEach
  void tearDown() {
    // 让后台流式任务结束，避免线程泄漏影响下一个测试
    try {
      Thread.sleep(100);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private void stubChatModel(String reply) {
    when(modelClientFactory.create(any(ModelConfigRevision.class)))
        .thenReturn(
            new ChatModel() {
              @Override
              public ChatResponse call(Prompt prompt) {
                return null;
              }

              @Override
              public Flux<ChatResponse> stream(Prompt prompt) {
                // 轻微延迟，保证断言期间 active slot 仍被认领（避免后台 finalizer 抢先释放）
                return Flux.just(
                        ChatResponse.builder()
                            .generations(List.of(new Generation(new AssistantMessage(reply))))
                            .build())
                    .delayElements(java.time.Duration.ofMillis(50));
              }
            });
  }

  private SendMessageRequest send(String clientId, String content) {
    SendMessageRequest req = new SendMessageRequest();
    req.setClientMessageId(clientId);
    req.setContent(content);
    req.setModelConfigId(modelConfig.getId());
    return req;
  }

  @Test
  @DisplayName("should persist user and assistant message and claim active slot on send")
  void shouldPersistMessagesOnSend() {
    stubChatModel("pong");
    SseEmitter emitter = generationService.send(conversation.getId(), send("client-1", "hello"));
    assertThat(emitter).isNotNull();

    List<ChatMessage> messages = conversationService.listMessages(conversation.getId(), null, 20);
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0).getRole()).isEqualTo(ChatMessage.ROLE_USER);
    assertThat(messages.get(0).getContent()).isEqualTo("hello");
    assertThat(messages.get(1).getRole()).isEqualTo(ChatMessage.ROLE_ASSISTANT);
    // Tx1 同步写入 GENERATING（active slot 认领在提交时完成）
    assertThat(messages.get(1).getGenerationStatus()).isEqualTo(ChatMessage.GENERATING);
  }

  @Test
  @DisplayName("should reject concurrent generation on the same conversation")
  void shouldRejectConcurrentGeneration() {
    stubChatModel("pong");
    generationService.send(conversation.getId(), send("client-2", "first"));

    SendMessageRequest second = send("client-3", "second");
    assertThatThrownBy(() -> generationService.send(conversation.getId(), second))
        .isInstanceOf(ActiveGenerationExistsException.class);
  }

  @Test
  @DisplayName("should return empty emitter on duplicate clientMessageId (idempotent)")
  void shouldBeIdempotentOnDuplicateClientMessageId() {
    stubChatModel("pong");
    generationService.send(conversation.getId(), send("client-dup", "hello"));

    // 相同 clientMessageId 重试：不创建新消息、不抛错
    SseEmitter emitter = generationService.send(conversation.getId(), send("client-dup", "hello"));
    assertThat(emitter).isNotNull();
    List<ChatMessage> messages = conversationService.listMessages(conversation.getId(), null, 20);
    assertThat(messages).hasSize(2);
  }

  @Test
  @DisplayName("should update conversation default model when modelConfigId provided")
  void shouldUpdateConversationDefaultModel() {
    stubChatModel("pong");
    generationService.send(conversation.getId(), send("client-4", "hello"));

    Conversation c = conversationService.getByIdAndOwner(conversation.getId());
    assertThat(c.getDefaultModelConfigId()).isEqualTo(modelConfig.getId());
  }

  @Test
  @DisplayName("should release active slot after generation finalizes")
  void shouldReleaseSlotAfterFinalize() throws Exception {
    stubChatModel("pong");
    generationService.send(conversation.getId(), send("client-5", "hello"));

    // 等待后台 streamer 完成（mock 立即返回流）
    awaitSlotReleased();
    Conversation c = conversationService.getByIdAndOwner(conversation.getId());
    assertThat(c.getActiveGenerationMessageId()).isNull();
  }

  @Test
  @DisplayName("should create new assistant attempt on regenerate")
  void shouldCreateNewAttemptOnRegenerate() throws Exception {
    stubChatModel("first-answer");
    generationService.send(conversation.getId(), send("client-r1", "question"));
    awaitSlotReleased();

    List<ChatMessage> before = conversationService.listMessages(conversation.getId(), null, 20);
    assertThat(before).hasSize(2);

    // 重新生成
    SseEmitter emitter = generationService.regenerate(conversation.getId(), null);
    assertThat(emitter).isNotNull();

    List<ChatMessage> after = conversationService.listMessages(conversation.getId(), null, 20);
    // user + 旧 assistant + 新 assistant(GENERATING)
    assertThat(after).hasSize(3);
    assertThat(after.get(2).getRole()).isEqualTo(ChatMessage.ROLE_ASSISTANT);
    assertThat(after.get(2).getGenerationStatus()).isEqualTo(ChatMessage.GENERATING);
    assertThat(after.get(2).getReplyToMessageId()).isEqualTo(after.get(0).getId());
  }

  @Test
  @DisplayName("should switch active attempt to the regenerated one on success")
  void shouldSwitchActiveOnRegenerateSuccess() throws Exception {
    stubChatModel("better-answer");
    generationService.send(conversation.getId(), send("client-r2", "question"));
    awaitSlotReleased();

    // 确认旧 attempt 为 active completed
    List<ChatMessage> messages = conversationService.listMessages(conversation.getId(), null, 20);
    ChatMessage oldAnswer = messages.get(1);
    assertThat(oldAnswer.getGenerationStatus()).isEqualTo(ChatMessage.COMPLETED);
    assertThat(oldAnswer.getIsActive()).isTrue();

    // 重新生成并等待完成
    generationService.regenerate(conversation.getId(), null);
    awaitSlotReleased();

    List<ChatMessage> after = conversationService.listMessages(conversation.getId(), null, 20);
    // 新 attempt 变为 active completed，旧 attempt 不再 active
    ChatMessage newAnswer = after.get(2);
    assertThat(newAnswer.getGenerationStatus()).isEqualTo(ChatMessage.COMPLETED);
    assertThat(newAnswer.getIsActive()).isTrue();
    assertThat(after.get(1).getIsActive()).isFalse();
  }

  @Test
  @DisplayName("should keep old active answer when regenerate fails")
  void shouldKeepOldActiveOnRegenerateFailure() throws Exception {
    // 第一次成功
    stubChatModel("good-answer");
    generationService.send(conversation.getId(), send("client-r3", "question"));
    awaitSlotReleased();

    // 第二次让 Provider 失败（mock 流抛错）
    when(modelClientFactory.create(any(ModelConfigRevision.class)))
        .thenReturn(
            new ChatModel() {
              @Override
              public ChatResponse call(Prompt prompt) {
                return null;
              }

              @Override
              public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.error(new RuntimeException("provider-failed"));
              }
            });
    generationService.regenerate(conversation.getId(), null);
    awaitSlotReleased();

    List<ChatMessage> after = conversationService.listMessages(conversation.getId(), null, 20);
    // 旧回答仍 active completed，新 attempt 标记 failed
    assertThat(after.get(1).getIsActive()).isTrue();
    assertThat(after.get(1).getGenerationStatus()).isEqualTo(ChatMessage.COMPLETED);
    assertThat(after.get(2).getGenerationStatus()).isEqualTo(ChatMessage.FAILED);
    assertThat(after.get(2).getIsActive()).isFalse();
  }

  private void awaitSlotReleased() throws InterruptedException {
    for (int i = 0; i < 50; i++) {
      Conversation c = conversationService.getByIdAndOwner(conversation.getId());
      if (c.getActiveGenerationMessageId() == null) {
        return;
      }
      Thread.sleep(100);
    }
    fail("active generation slot not released within 5s");
  }
}
