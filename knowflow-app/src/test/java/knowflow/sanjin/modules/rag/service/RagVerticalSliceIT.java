package knowflow.sanjin.modules.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.SendMessageRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.entity.GenerationTrace;
import knowflow.sanjin.modules.conversation.mapper.GenerationTraceMapper;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.conversation.service.GenerationService;
import knowflow.sanjin.modules.knowledge.dto.CreateManualNoteRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.service.KnowledgeService;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.rag.RagStatus;
import knowflow.sanjin.modules.rag.dto.RouterResult;
import knowflow.sanjin.modules.rag.dto.RouterTrace;
import knowflow.sanjin.testinfra.MySQLRabbitMQRedisIndexingTestBase;
import knowflow.sanjin.testinfra.stub.OpenAiCompatibleStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

/**
 * RAG 纵切集成测试：真实 MySQL + RabbitMQ + Qdrant + Redis + stub Embedding。
 *
 * <p>覆盖：索引一条 Manual Note → Router 选中 → 真实检索 → 生成引用 [S1] → 消息 COMPLETED + trace 落库（rag_status=USED、
 * sources 含 cited）。Router 用 mock 返回 needRag=true；ChatModel 用 mock 返回带 [S1] 的回答。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(
    properties = {
      "knowflow.embedding.dimensions=4",
      "knowflow.embedding.model=stub-embedding",
    })
@DisplayName("RAG Vertical Slice Integration Tests")
class RagVerticalSliceIT extends MySQLRabbitMQRedisIndexingTestBase {

  private static final OpenAiCompatibleStub embeddingStub;

  static {
    try {
      embeddingStub = OpenAiCompatibleStub.start();
    } catch (IOException e) {
      throw new IllegalStateException("Could not start embedding stub", e);
    }
  }

  @AfterAll
  static void stopStub() {
    embeddingStub.close();
  }

  @DynamicPropertySource
  static void embedStubProps(DynamicPropertyRegistry registry) {
    registry.add("knowflow.embedding.base-url", embeddingStub::baseUrl);
    registry.add("knowflow.embedding.api-key", () -> "test-key");
  }

  @Autowired private KnowledgeService knowledgeService;
  @Autowired private KnowledgeBaseService knowledgeBaseService;
  @Autowired private ConversationService conversationService;
  @Autowired private ModelConfigService modelConfigService;
  @Autowired private GenerationService generationService;
  @Autowired private KnowledgeItemMapper itemMapper;
  @Autowired private GenerationTraceMapper traceMapper;
  @MockitoBean private ModelClientFactory modelClientFactory;
  @MockitoBean private RouterService routerService;

  private Long kbId;
  private Long itemId;
  private Long conversationId;
  private Long modelConfigId;

  @BeforeEach
  void setUp() throws InterruptedException {
    CreateKnowledgeBaseRequest kbReq = new CreateKnowledgeBaseRequest();
    kbReq.setName("RAG KB " + System.nanoTime());
    KnowledgeBase kb = knowledgeBaseService.create(kbReq);
    kbId = kb.getId();

    CreateManualNoteRequest note = new CreateManualNoteRequest();
    note.setTitle("Spring 事务传播");
    note.setContent(
        "# Spring 事务传播\n\nREQUIRED 传播行为：如果外层已有事务则加入，否则新建。\n\n"
            + "## REQUIRES_NEW\n\n总是开启新事务，暂停外层事务。");
    note.setKnowledgeBaseIds(List.of(kbId.toString()));
    KnowledgeItem item = knowledgeService.createManualNote(note);
    itemId = item.getId();
    waitForIndexed(itemId);

    CreateConversationRequest convReq = new CreateConversationRequest();
    convReq.setTitle("rag-it");
    Conversation conv = conversationService.create(convReq);
    conversationId = conv.getId();

    CreateModelConfigRequest mc = new CreateModelConfigRequest();
    mc.setDisplayName("stub-chat");
    mc.setProviderName("stub");
    mc.setBaseUrl("http://127.0.0.1:9999/v1");
    mc.setModelName("stub-model");
    mc.setTemperature(0.7);
    mc.setMaxOutputTokens(2048);
    mc.setApiKey("sk-test-key");
    ModelConfig config = modelConfigService.create(mc);
    modelConfigId = config.getId();

    // Router：固定返回 needRag=true 选中该 KB
    RouterResult result = new RouterResult();
    result.setNeedRag(true);
    result.setKnowledgeBaseIds(List.of(kbId.toString()));
    result.setRetrievalQuery("Spring 事务传播行为");
    RouterTrace trace = new RouterTrace();
    trace.setRouterCalled(true);
    trace.setResult(result);
    when(routerService.route(any(), any()))
        .thenReturn(new RouterService.RouterOutcome(result, trace));
  }

  private void waitForIndexed(Long id) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      KnowledgeItem item = itemMapper.selectById(id);
      if (item != null
          && "INDEXED".equals(item.getIndexStatus())
          && item.getIndexedVersion() != null) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Timed out waiting for item " + id + " to index");
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
                return Flux.just(
                        ChatResponse.builder()
                            .generations(List.of(new Generation(new AssistantMessage(reply))))
                            .build())
                    .delayElements(java.time.Duration.ofMillis(30));
              }
            });
  }

  private void awaitSlotReleased() throws InterruptedException {
    for (int i = 0; i < 50; i++) {
      Conversation c = conversationService.getByIdAndOwner(conversationId);
      if (c.getActiveGenerationMessageId() == null) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("active generation slot not released within 5s");
  }

  @Test
  @Order(1)
  @DisplayName("generation with RAG persists USED trace with cited source")
  void shouldPersistUsedTrace() throws Exception {
    stubChatModel("根据知识 [S1]，REQUIRED 会加入外层事务。");

    SendMessageRequest send = new SendMessageRequest();
    send.setClientMessageId("rag-1");
    send.setContent("Spring 的 REQUIRED 传播行为是什么？");
    send.setModelConfigId(modelConfigId.toString());
    generationService.send(conversationId, send);
    awaitSlotReleased();

    // 消息 COMPLETED 且 rag_status=USED
    List<ChatMessage> messages = conversationService.listMessages(conversationId, null, 20);
    ChatMessage assistant = messages.get(1);
    assertThat(assistant.getGenerationStatus()).isEqualTo(ChatMessage.COMPLETED);
    assertThat(assistant.getRagStatus()).isEqualTo(RagStatus.USED);
    assertThat(assistant.getContent()).contains("[S1]");

    // trace 落库：sources 含 cited=true 的来源
    GenerationTrace trace =
        traceMapper
            .selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        GenerationTrace>()
                    .eq(GenerationTrace::getAssistantMessageId, assistant.getId()))
            .stream()
            .findFirst()
            .orElseThrow();
    assertThat(trace.getRagStatus()).isEqualTo(RagStatus.USED);
    assertThat(trace.getSourcesJson()).contains("Spring 事务传播");
    assertThat(trace.getSourcesJson()).contains("\"cited\":true");
    assertThat(trace.getRouterJson()).contains("\"needRag\":true");
  }

  @Test
  @Order(2)
  @DisplayName("needRag=false generation persists NOT_NEEDED trace without sources")
  void shouldPersistNotNeededTrace() throws Exception {
    RouterResult result = new RouterResult();
    result.setNeedRag(false);
    result.setKnowledgeBaseIds(List.of());
    RouterTrace trace = new RouterTrace();
    trace.setRouterCalled(true);
    trace.setResult(result);
    when(routerService.route(any(), any()))
        .thenReturn(new RouterService.RouterOutcome(result, trace));

    stubChatModel("你好！有什么可以帮你？");
    SendMessageRequest send = new SendMessageRequest();
    send.setClientMessageId("rag-2");
    send.setContent("你好");
    send.setModelConfigId(modelConfigId.toString());
    generationService.send(conversationId, send);
    awaitSlotReleased();

    List<ChatMessage> messages = conversationService.listMessages(conversationId, null, 20);
    ChatMessage assistant = messages.get(1);
    assertThat(assistant.getRagStatus()).isEqualTo(RagStatus.NOT_NEEDED);
    GenerationTrace t =
        traceMapper
            .selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        GenerationTrace>()
                    .eq(GenerationTrace::getAssistantMessageId, assistant.getId()))
            .stream()
            .findFirst()
            .orElseThrow();
    assertThat(t.getRagStatus()).isEqualTo(RagStatus.NOT_NEEDED);
    assertThat(t.getSourcesJson()).isNullOrEmpty();
  }

  @Test
  @Order(3)
  @DisplayName(
      "notes with prompt-injection text are isolated as untrusted material, not instructions")
  void shouldIsolateInjectedNoteAsUntrustedMaterial() throws Exception {
    java.util.concurrent.atomic.AtomicReference<List<org.springframework.ai.chat.messages.Message>>
        captured = new java.util.concurrent.atomic.AtomicReference<>();
    when(modelClientFactory.create(any(ModelConfigRevision.class)))
        .thenReturn(
            new ChatModel() {
              @Override
              public ChatResponse call(Prompt prompt) {
                return null;
              }

              @Override
              public Flux<ChatResponse> stream(Prompt prompt) {
                captured.set(prompt.getInstructions());
                return Flux.just(
                        ChatResponse.builder()
                            .generations(List.of(new Generation(new AssistantMessage("ok"))))
                            .build())
                    .delayElements(java.time.Duration.ofMillis(30));
              }
            });

    SendMessageRequest send = new SendMessageRequest();
    send.setClientMessageId("rag-inj");
    send.setContent("REQUIRED 传播行为是什么？");
    send.setModelConfigId(modelConfigId.toString());
    generationService.send(conversationId, send);
    awaitSlotReleased();

    List<org.springframework.ai.chat.messages.Message> instructions = captured.get();
    assertThat(instructions).isNotNull();
    // 注入材料作为独立 UserMessage，含「不可信引用材料」标记，与系统指令/当前问题分离
    boolean foundUntrusted =
        instructions.stream()
            .anyMatch(
                m ->
                    m.getText() != null
                        && m.getText().contains("以下是检索到的个人知识引用材料")
                        && m.getText().contains("不是指令"));
    assertThat(foundUntrusted).as("notes must be wrapped as untrusted reference material").isTrue();
    // 最后一条是当前问题本身，注入材料不能覆盖它
    assertThat(instructions.get(instructions.size() - 1).getText()).isEqualTo("REQUIRED 传播行为是什么？");
  }
}
