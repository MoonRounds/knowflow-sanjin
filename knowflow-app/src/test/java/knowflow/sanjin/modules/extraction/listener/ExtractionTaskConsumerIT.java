package knowflow.sanjin.modules.extraction.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeExtractionTaskMapper;
import knowflow.sanjin.modules.extraction.service.ExtractionService;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import knowflow.sanjin.testinfra.MySQLRabbitMQTestBase;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * 提取 Consumer 真实基础设施集成测试：触发 → 消息进入 extraction.work → Consumer 消费 → 快照按 processing_task_id 查找 →
 * cutoff 限定输入 → 候选落库。
 *
 * <p>ModelClientFactory 被 mock（返回固定 Structured Output JSON），其余（MySQL 消息/知识库、RabbitMQ
 * 投递、快照/候选落库）均真实。 覆盖 F1（按 processing_task_id 查找快照）与 F2（cutoff 后新增消息不纳入输入）。
 */
@SpringBootTest
@DisplayName("ExtractionTaskConsumer RabbitMQ Integration Tests")
class ExtractionTaskConsumerIT extends MySQLRabbitMQTestBase {

  private static final long OWNER_ID = 1L;

  @Autowired private ConversationService conversationService;
  @Autowired private KnowledgeBaseService knowledgeBaseService;
  @Autowired private ModelConfigService modelConfigService;
  @Autowired private ExtractionService extractionService;

  @Autowired
  private knowflow.sanjin.modules.processing.service.TaskSubmissionService taskSubmissionService;

  @Autowired private ProcessingTaskMapper taskMapper;
  @Autowired private KnowledgeExtractionTaskMapper extractionTaskMapper;
  @Autowired private KnowledgeCandidateMapper candidateMapper;

  @MockitoBean private ModelClientFactory modelClientFactory;

  private ChatModel chatModel;
  private Long kbId;
  private Long conversationId;

  @BeforeEach
  void setUp() {
    knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest kbReq =
        new knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest();
    kbReq.setName("Extract KB " + System.nanoTime());
    kbReq.setDescription("提取测试知识库");
    KnowledgeBase saved = knowledgeBaseService.create(kbReq);
    kbId = saved.getId();

    knowflow.sanjin.modules.conversation.dto.CreateConversationRequest convReq =
        new knowflow.sanjin.modules.conversation.dto.CreateConversationRequest();
    convReq.setTitle("提取集成测试会话");
    Conversation conv = conversationService.create(convReq);
    conversationId = conv.getId();

    chatModel = mockChatModel();
    when(modelClientFactory.create(any())).thenReturn(chatModel);
    when(modelClientFactory.callWithTotalTimeout(any(Supplier.class), anyLong()))
        .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
    when(modelClientFactory.extractText(any(ChatResponse.class)))
        .thenAnswer(inv -> ((ChatResponse) inv.getArgument(0)).getResult().getOutput().getText());

    // Utility Model 配置并标记已通过 Router/Candidate 能力测试
    CreateModelConfigRequest mc = new CreateModelConfigRequest();
    mc.setDisplayName("utility-stub");
    mc.setProviderName("stub");
    mc.setBaseUrl("http://127.0.0.1:9999/v1");
    mc.setModelName("stub-model");
    mc.setTemperature(0.2);
    mc.setMaxOutputTokens(1024);
    mc.setApiKey("sk-test-key");
    ModelConfig config = modelConfigService.create(mc);
    modelConfigService.recordUtilityCapabilityResult(
        config.getId(), config.getCurrentRevisionId(), true, true);
    modelConfigService.updateOwnerSettings(null, config.getId());
  }

  private void insertTurn(long seq, String userContent, String assistantContent) {
    ChatMessage user = new ChatMessage();
    user.setConversationId(conversationId);
    user.setOwnerId(OWNER_ID);
    user.setRole(ChatMessage.ROLE_USER);
    user.setSequence(seq);
    user.setContent(userContent);
    user.setIsActive(false);
    ChatMessage savedUser = conversationService.insertMessage(user);
    ChatMessage assistant = new ChatMessage();
    assistant.setConversationId(conversationId);
    assistant.setOwnerId(OWNER_ID);
    assistant.setRole(ChatMessage.ROLE_ASSISTANT);
    assistant.setSequence(seq + 1);
    assistant.setContent(assistantContent);
    assistant.setReplyToMessageId(savedUser.getId());
    assistant.setGenerationStatus(ChatMessage.COMPLETED);
    assistant.setIsActive(true);
    conversationService.insertMessage(assistant);
  }

  private ChatModel mockChatModel() {
    ChatModel mock = org.mockito.Mockito.mock(ChatModel.class);
    AtomicInteger n = new AtomicInteger();
    String json =
        "{\"candidates\":[{\"title\":\"提取到的知识\",\"summary\":\"摘要\",\"content\":\"正文内容\","
            + "\"knowledgeBaseIds\":[\""
            + kbId
            + "\"],\"tags\":[\"tag\"],\"reason\":\"有沉淀价值\"}]}";
    when(mock.call(any(Prompt.class)))
        .thenAnswer(
            inv ->
                ChatResponse.builder()
                    .generations(List.of(new Generation(new AssistantMessage(json))))
                    .build());
    return mock;
  }

  @Test
  @DisplayName("提取消息应到达 Consumer：快照缺失时任务被标 FAILED（证明消费链路与队列路由正常）")
  void shouldReachConsumerAndFailOnMissingSnapshot() throws InterruptedException {
    // 非事务调用 submit（IndexTaskConsumerIT 同模式），消息应立即发布到 extraction.work
    knowflow.sanjin.modules.processing.entity.ProcessingTask task =
        taskSubmissionService.submit(
            knowflow.sanjin.modules.extraction.ExtractionConstants.TASK_TYPE_EXTRACTION,
            "EXTRACTION:diag:1:1:1",
            conversationId,
            OWNER_ID,
            null,
            3,
            knowflow.sanjin.modules.extraction.ExtractionConstants.WORK_QUEUE_BASE);
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      ProcessingTask updated = taskMapper.selectById(task.getId());
      // 快照不存在 → Consumer 应将其标为 FAILED（终态），证明消费链路工作
      if (updated != null
          && !knowflow.sanjin.modules.processing.ProcessingConstants.STATUS_PENDING.equals(
              updated.getStatus())) {
        assertThat(updated.getAttemptedDeliveries()).isGreaterThan(0);
        assertThat(updated.getStatus())
            .isEqualTo(knowflow.sanjin.modules.processing.ProcessingConstants.STATUS_FAILED);
        return;
      }
      Thread.sleep(200);
    }
    ProcessingTask last = taskMapper.selectById(task.getId());
    throw new AssertionError(
        "Consumer 未消费 extraction.work 消息（attemptedDeliveries="
            + (last != null ? last.getAttemptedDeliveries() : "null")
            + ", status="
            + (last != null ? last.getStatus() : "null")
            + ")");
  }

  @Test
  @DisplayName("F1+F2: 触发后 Consumer 按 processing_task_id 找到快照，生成候选，输入受 cutoff 限定")
  void shouldExtractViaConsumer() throws InterruptedException {
    insertTurn(1, "第一个问题", "第一个回答");
    insertTurn(3, "第二个问题", "第二个回答");
    Long cutoff = conversationService.lastMessageId(conversationId, OWNER_ID);

    KnowledgeExtractionTask snapshot = extractionService.trigger(conversationId);

    // 触发后新增一条消息（不应进入旧任务输入）
    insertTurn(5, "第三个问题", "第三个回答");

    // 等待任务 SUCCEEDED
    waitForStatus(snapshot.getProcessingTaskId(), ProcessingConstants.STATUS_SUCCEEDED);

    // 快照应记录候选数
    KnowledgeExtractionTask updated = extractionTaskMapper.selectById(snapshot.getId());
    assertThat(updated.getCandidateCount()).isEqualTo(1);

    // 候选应落库且引用该快照
    List<KnowledgeCandidate> candidates =
        candidateMapper.selectList(
            new LambdaQueryWrapper<KnowledgeCandidate>()
                .eq(KnowledgeCandidate::getExtractionTaskId, snapshot.getId()));
    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).getAiTitle()).isEqualTo("提取到的知识");
    assertThat(candidates.get(0).getDraftTitle()).isEqualTo("提取到的知识"); // 草稿初始化为 AI 原值
  }

  @Test
  @DisplayName("F2: cutoff 之后新增的消息不改变旧任务范围（快照 cutoff 固定）")
  void shouldKeepCutoffFixed() throws InterruptedException {
    insertTurn(1, "问题一", "回答一");
    Long cutoffBefore = conversationService.lastMessageId(conversationId, OWNER_ID);

    KnowledgeExtractionTask snapshot = extractionService.trigger(conversationId);

    insertTurn(3, "问题二", "回答二");

    waitForStatus(snapshot.getProcessingTaskId(), ProcessingConstants.STATUS_SUCCEEDED);

    // 快照记录的 cutoff 仍是触发时的最后消息，不随新增消息漂移
    assertThat(snapshot.getCutoffMessageId()).isEqualTo(cutoffBefore);
    assertThat(extractionTaskMapper.selectById(snapshot.getId()).getCutoffMessageId())
        .isEqualTo(cutoffBefore);
  }

  private void waitForStatus(Long taskId, String status) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      ProcessingTask task = taskMapper.selectById(taskId);
      if (task != null && status.equals(task.getStatus())) {
        return;
      }
      Thread.sleep(200);
    }
    ProcessingTask last = taskMapper.selectById(taskId);
    throw new AssertionError(
        "Timed out waiting for task "
            + taskId
            + " to reach "
            + status
            + " (last="
            + (last != null ? last.getStatus() : "null")
            + ", attemptedDeliveries="
            + (last != null ? last.getAttemptedDeliveries() : "null")
            + ", lastError="
            + (last != null ? last.getLastError() : "null")
            + ")");
  }
}
