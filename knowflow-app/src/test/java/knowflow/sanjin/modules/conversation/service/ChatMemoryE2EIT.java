package knowflow.sanjin.modules.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.SendMessageRequest;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.memory.MemoryService;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.testinfra.RedisMemoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

/**
 * Chat Memory 端到端集成测试：真实 MySQL + Redis + mock ChatModel。
 *
 * <p>覆盖：Generation 成功后投影刷新、多轮上下文重建、regenerate 后旧 answer 移出、跨 Conversation 隔离、删除清理。
 */
@SpringBootTest
@DisplayName("Chat Memory E2E Integration Tests")
class ChatMemoryE2EIT extends RedisMemoryTestBase {

  @Autowired private ConversationService conversationService;
  @Autowired private ModelConfigService modelConfigService;
  @Autowired private GenerationService generationService;
  @Autowired private MemoryService memoryService;
  @MockitoBean private ModelClientFactory modelClientFactory;

  private final AtomicReference<String> latestReply = new AtomicReference<>("pong");
  private Conversation conversation;
  private ModelConfig modelConfig;

  @AfterEach
  void tearDown() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private void setUpConversation() {
    CreateConversationRequest req = new CreateConversationRequest();
    req.setTitle("memory-e2e");
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

  private void stubChatModel() {
    when(modelClientFactory.create(any(ModelConfigRevision.class)))
        .thenReturn(
            new ChatModel() {
              @Override
              public ChatResponse call(Prompt prompt) {
                return null;
              }

              @Override
              public Flux<ChatResponse> stream(Prompt prompt) {
                String reply = latestReply.get();
                return Flux.just(
                        ChatResponse.builder()
                            .generations(List.of(new Generation(new AssistantMessage(reply))))
                            .build())
                    .delayElements(java.time.Duration.ofMillis(30));
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

  private void awaitSlotReleased() throws InterruptedException {
    awaitSlotReleased(conversation);
  }

  private void awaitSlotReleased(Conversation target) throws InterruptedException {
    for (int i = 0; i < 50; i++) {
      Conversation c = conversationService.getByIdAndOwner(target.getId());
      if (c.getActiveGenerationMessageId() == null) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("active generation slot not released within 5s");
  }

  @Test
  @DisplayName("generation success refreshes Redis projection with completed active turns")
  void shouldRefreshProjectionOnSuccess() throws Exception {
    setUpConversation();
    stubChatModel();
    generationService.send(conversation.getId(), send("e2e-1", "user question"));
    awaitSlotReleased();

    List<org.springframework.ai.chat.messages.Message> window =
        memoryService.loadWindow(conversation.getId());
    assertThat(window).hasSize(2);
    assertThat(window.get(0).getText()).isEqualTo("user question");
    assertThat(window.get(1).getText()).isEqualTo("pong");
  }

  @Test
  @DisplayName("multi-turn conversation keeps full turn context in projection")
  void shouldKeepMultiTurnContext() throws Exception {
    setUpConversation();
    stubChatModel();

    latestReply.set("reply-1");
    generationService.send(conversation.getId(), send("e2e-m1", "turn 1 question"));
    awaitSlotReleased();

    latestReply.set("reply-2");
    generationService.send(conversation.getId(), send("e2e-m2", "turn 2 question"));
    awaitSlotReleased();

    List<org.springframework.ai.chat.messages.Message> window =
        memoryService.loadWindow(conversation.getId());
    assertThat(window).hasSize(4);
    assertThat(window.get(0).getText()).isEqualTo("turn 1 question");
    assertThat(window.get(1).getText()).isEqualTo("reply-1");
    assertThat(window.get(2).getText()).isEqualTo("turn 2 question");
    assertThat(window.get(3).getText()).isEqualTo("reply-2");
  }

  @Test
  @DisplayName("regenerate moves old active answer out of the projection")
  void shouldRefreshProjectionAfterRegenerate() throws Exception {
    setUpConversation();
    stubChatModel();

    latestReply.set("first-answer");
    generationService.send(conversation.getId(), send("e2e-r1", "question"));
    awaitSlotReleased();

    latestReply.set("better-answer");
    generationService.regenerate(conversation.getId(), null);
    awaitSlotReleased();

    List<org.springframework.ai.chat.messages.Message> window =
        memoryService.loadWindow(conversation.getId());
    assertThat(window).hasSize(2);
    assertThat(window.get(0).getText()).isEqualTo("question");
    assertThat(window.get(1).getText()).isEqualTo("better-answer");
  }

  @Test
  @DisplayName("clearing Redis then continuing keeps context via rebuild from MySQL")
  void shouldRebuildFromMysqlAfterRedisClear() throws Exception {
    setUpConversation();
    stubChatModel();

    latestReply.set("reply-1");
    generationService.send(conversation.getId(), send("e2e-c1", "q1"));
    awaitSlotReleased();

    // 清空 Redis，模拟投影丢失
    clearRedis();

    List<org.springframework.ai.chat.messages.Message> window =
        memoryService.loadWindow(conversation.getId());
    assertThat(window).hasSize(2);
    assertThat(window.get(0).getText()).isEqualTo("q1");
    assertThat(window.get(1).getText()).isEqualTo("reply-1");
  }

  @Test
  @DisplayName("two conversations do not share memory")
  void shouldIsolateAcrossConversations() throws Exception {
    setUpConversation();
    stubChatModel();

    CreateConversationRequest req2 = new CreateConversationRequest();
    req2.setTitle("memory-e2e-2");
    Conversation conv2 = conversationService.create(req2);

    latestReply.set("ans-1");
    generationService.send(conversation.getId(), send("e2e-i1", "q in conv1"));
    awaitSlotReleased();

    latestReply.set("ans-2");
    generationService.send(conv2.getId(), send("e2e-i2", "q in conv2"));
    awaitSlotReleased(conv2);

    List<org.springframework.ai.chat.messages.Message> w1 =
        memoryService.loadWindow(conversation.getId());
    List<org.springframework.ai.chat.messages.Message> w2 = memoryService.loadWindow(conv2.getId());
    assertThat(w1).hasSize(2);
    assertThat(w2).hasSize(2);
    assertThat(w1.get(0).getText()).isEqualTo("q in conv1");
    assertThat(w2.get(0).getText()).isEqualTo("q in conv2");
    assertThat(w1.get(1).getText()).isEqualTo("ans-1");
    assertThat(w2.get(1).getText()).isEqualTo("ans-2");
  }

  @Test
  @DisplayName("soft delete clears the conversation memory")
  void shouldClearMemoryOnDelete() throws Exception {
    setUpConversation();
    stubChatModel();

    generationService.send(conversation.getId(), send("e2e-del-1", "q"));
    awaitSlotReleased();

    assertThat(memoryService.loadWindow(conversation.getId())).hasSize(2);

    conversationService.softDelete(conversation.getId());
    // 删除事务提交后清理投影（Controller 层调用；此处直接触发同一路径）
    memoryService.clear(conversation.getId());
    assertThat(memoryService.loadWindow(conversation.getId())).isEmpty();
  }

  @Test
  @DisplayName("window keeps only the most recent N active turns beyond the limit")
  void shouldKeepOnlyRecentTurnsBeyondLimit() throws Exception {
    setUpConversation();
    stubChatModel();

    // 超过默认 10 轮窗口：发 13 个 active Turn
    for (int i = 1; i <= 13; i++) {
      latestReply.set("reply-" + i);
      generationService.send(conversation.getId(), send("e2e-w" + i, "turn " + i));
      awaitSlotReleased();
    }

    List<org.springframework.ai.chat.messages.Message> window =
        memoryService.loadWindow(conversation.getId());
    // 只保留最近 10 轮 = 20 条消息
    assertThat(window).hasSize(20);
    assertThat(window.get(0).getText()).isEqualTo("turn 4");
    assertThat(window.get(1).getText()).isEqualTo("reply-4");
    assertThat(window.get(18).getText()).isEqualTo("turn 13");
    assertThat(window.get(19).getText()).isEqualTo("reply-13");
  }

  @Test
  @DisplayName("failed turn does not enter the memory projection")
  void shouldExcludeFailedTurnFromMemory() throws Exception {
    setUpConversation();
    stubChatModel();

    // 第一个成功 turn 进入投影
    latestReply.set("good-reply");
    generationService.send(conversation.getId(), send("e2e-f1", "turn 1"));
    awaitSlotReleased();
    assertThat(memoryService.loadWindow(conversation.getId())).hasSize(2);

    // 第二个 turn 让 Provider 失败（mock 流抛错）
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
    latestReply.set("never-used");
    generationService.send(conversation.getId(), send("e2e-f2", "turn 2"));
    awaitSlotReleased();

    List<org.springframework.ai.chat.messages.Message> window =
        memoryService.loadWindow(conversation.getId());
    // 失败 turn 不入投影：仍只有第一个成功 turn
    assertThat(window).hasSize(2);
    assertThat(window.get(0).getText()).isEqualTo("turn 1");
    assertThat(window.get(1).getText()).isEqualTo("good-reply");
  }

  @Test
  @DisplayName("conversation continues from MySQL when Redis is unavailable, then recovers")
  void shouldDegradeToMysqlWhenRedisDownAndRecover() throws Exception {
    setUpConversation();
    stubChatModel();

    latestReply.set("reply-1");
    generationService.send(conversation.getId(), send("e2e-d1", "turn 1"));
    awaitSlotReleased();
    assertThat(memoryService.loadWindow(conversation.getId())).hasSize(2);

    // Redis 停机：暂停响应 4 秒，期间读写超时触发 MySQL 降级
    pauseRedis(4000);
    try {
      // 停机期间对话仍成功（MySQL 兜底构造上下文，Redis 读写失败被容错）
      latestReply.set("reply-2");
      generationService.send(conversation.getId(), send("e2e-d2", "turn 2"));
      awaitSlotReleased();

      // 停机期间直接从 MySQL 构造（Redis 不可达），turn 2 上下文不丢
      List<org.springframework.ai.chat.messages.Message> window =
          memoryService.loadWindow(conversation.getId());
      assertThat(window).hasSize(4);
      assertThat(window.get(2).getText()).isEqualTo("turn 2");
      assertThat(window.get(3).getText()).isEqualTo("reply-2");
    } finally {
      // 等待 pause 结束，Redis 恢复
      Thread.sleep(4500);
    }

    // 恢复后下一次对话刷新投影自愈，MySQL 事实源保证完整上下文
    latestReply.set("reply-3");
    generationService.send(conversation.getId(), send("e2e-d3", "turn 3"));
    awaitSlotReleased();

    List<org.springframework.ai.chat.messages.Message> recovered =
        memoryService.loadWindow(conversation.getId());
    // 三轮回合全部进入投影（含停机期间的 turn 2，从 MySQL 重建）
    assertThat(recovered).hasSize(6);
    assertThat(recovered.get(2).getText()).isEqualTo("turn 2");
    assertThat(recovered.get(3).getText()).isEqualTo("reply-2");
    assertThat(recovered.get(4).getText()).isEqualTo("turn 3");
    assertThat(recovered.get(5).getText()).isEqualTo("reply-3");
  }
}
