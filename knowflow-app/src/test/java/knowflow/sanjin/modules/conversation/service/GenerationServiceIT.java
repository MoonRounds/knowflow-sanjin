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
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.rag.RagStatus;
import knowflow.sanjin.modules.rag.dto.RagContext;
import knowflow.sanjin.modules.rag.service.RagContextBuilder;
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
  @Autowired private KnowledgeBaseService knowledgeBaseService;
  @MockitoBean private ModelClientFactory modelClientFactory;
  @MockitoBean private RagContextBuilder ragContextBuilder;

  private Conversation conversation;
  private ModelConfig modelConfig;

  @BeforeEach
  void setUp() {
    // 默认无 RAG：每个 generation 都是 NOT_AVAILABLE（普通生成）
    when(ragContextBuilder.build(any(), any()))
        .thenReturn(RagContext.simple(RagStatus.NOT_AVAILABLE));
    when(ragContextBuilder.build(any(), any(), anyList()))
        .thenReturn(RagContext.simple(RagStatus.NOT_AVAILABLE));

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
    req.setModelConfigId(modelConfig.getId().toString());
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
  @DisplayName("send freezes manual knowledge base bindings before async RAG execution")
  void shouldFreezeKnowledgeBaseBindingsForGeneration() throws Exception {
    CreateKnowledgeBaseRequest firstRequest = new CreateKnowledgeBaseRequest();
    firstRequest.setName("generation-snapshot-a-" + System.nanoTime());
    var first = knowledgeBaseService.create(firstRequest);
    CreateKnowledgeBaseRequest secondRequest = new CreateKnowledgeBaseRequest();
    secondRequest.setName("generation-snapshot-b-" + System.nanoTime());
    var second = knowledgeBaseService.create(secondRequest);

    var bindFirst = new knowflow.sanjin.modules.conversation.dto.UpdateConversationRequest();
    bindFirst.setKnowledgeBaseIds(List.of(first.getId().toString()));
    bindFirst.setRowVersion(conversation.getRowVersion().longValue());
    conversation = conversationService.update(conversation.getId(), bindFirst);

    stubChatModel("pong");
    generationService.send(conversation.getId(), send("client-snapshot", "snapshot-question"));

    var bindSecond = new knowflow.sanjin.modules.conversation.dto.UpdateConversationRequest();
    bindSecond.setKnowledgeBaseIds(List.of(second.getId().toString()));
    bindSecond.setRowVersion(conversation.getRowVersion().longValue());
    conversationService.update(conversation.getId(), bindSecond);

    verify(ragContextBuilder, timeout(3000))
        .build(conversation.getId(), "snapshot-question", List.of(first.getId()));
    awaitSlotReleased();
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
  @DisplayName("regenerate 覆盖最新 assistant 消息，不追加新消息")
  void shouldOverwriteLatestAssistantOnRegenerate() throws Exception {
    stubChatModel("first-answer");
    generationService.send(conversation.getId(), send("client-r1", "question"));
    awaitSlotReleased();

    List<ChatMessage> before = conversationService.listMessages(conversation.getId(), null, 20);
    assertThat(before).hasSize(2);
    Long assistantId = before.get(1).getId();
    assertThat(before.get(1).getContent()).isEqualTo("first-answer");
    // 前置条件：上一轮完成留下了终态 RAG 状态，验证重新生成会真正清空它（updateById 默认忽略 null 字段）
    assertThat(before.get(1).getRagStatus()).isEqualTo(RagStatus.NOT_AVAILABLE);

    // 重新生成：同一条 assistant 消息被清空并标记生成中
    SseEmitter emitter = generationService.regenerate(conversation.getId(), null);
    assertThat(emitter).isNotNull();

    List<ChatMessage> after = conversationService.listMessages(conversation.getId(), null, 20);
    assertThat(after).hasSize(2);
    assertThat(after.get(1).getId()).isEqualTo(assistantId);
    assertThat(after.get(1).getRole()).isEqualTo(ChatMessage.ROLE_ASSISTANT);
    assertThat(after.get(1).getGenerationStatus()).isEqualTo(ChatMessage.GENERATING);
    assertThat(after.get(1).getContent()).isEmpty();
    // 回归：终态元数据必须真正写为 null，而非残留上一轮的 ragStatus
    assertThat(after.get(1).getRagStatus()).isNull();
    assertThat(after.get(1).getErrorCode()).isNull();
  }

  @Test
  @DisplayName("regenerate 完成后原消息被新内容覆盖为 active completed")
  void shouldOverwriteContentOnRegenerateSuccess() throws Exception {
    stubChatModel("better-answer");
    generationService.send(conversation.getId(), send("client-r2", "question"));
    awaitSlotReleased();

    List<ChatMessage> messages = conversationService.listMessages(conversation.getId(), null, 20);
    Long assistantId = messages.get(1).getId();
    assertThat(messages.get(1).getGenerationStatus()).isEqualTo(ChatMessage.COMPLETED);
    assertThat(messages.get(1).getIsActive()).isTrue();

    // 重新生成并等待完成
    generationService.regenerate(conversation.getId(), null);
    awaitSlotReleased();

    List<ChatMessage> after = conversationService.listMessages(conversation.getId(), null, 20);
    // 同一条消息被新内容覆盖，仍是 active completed
    assertThat(after).hasSize(2);
    assertThat(after.get(1).getId()).isEqualTo(assistantId);
    assertThat(after.get(1).getGenerationStatus()).isEqualTo(ChatMessage.COMPLETED);
    assertThat(after.get(1).getIsActive()).isTrue();
    assertThat(after.get(1).getContent()).isEqualTo("better-answer");
  }

  @Test
  @DisplayName("regenerate 失败时原消息标记 failed，不再保留旧回答")
  void shouldMarkFailedOnRegenerateFailure() throws Exception {
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
    // 覆盖语义：原内容已清空，失败后同一消息标记 failed，旧回答不再保留
    assertThat(after).hasSize(2);
    assertThat(after.get(1).getGenerationStatus()).isEqualTo(ChatMessage.FAILED);
    assertThat(after.get(1).getIsActive()).isFalse();
    assertThat(after.get(1).getContent()).isEmpty();
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
