package knowflow.sanjin.modules.conversation.service;

import static org.assertj.core.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.UpdateConversationRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.entity.GenerationTrace;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.conversation.exception.ConversationExtractionInProgressException;
import knowflow.sanjin.modules.conversation.exception.ConversationKnowledgeBaseDisabledException;
import knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException;
import knowflow.sanjin.modules.conversation.exception.ConversationVersionConflictException;
import knowflow.sanjin.modules.conversation.mapper.ChatMessageMapper;
import knowflow.sanjin.modules.conversation.mapper.ConversationMapper;
import knowflow.sanjin.modules.conversation.mapper.GenerationTraceMapper;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeExtractionTaskMapper;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.modelconfig.dto.CreateModelConfigRequest;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfig;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

/** Conversation/Message 集成测试：迁移、CRUD、硬删除级联、sequence 游标与 Owner 隔离。 */
@SpringBootTest
@DisplayName("Conversation Integration Tests")
class ConversationServiceIT extends MySQLTestBase {

  @Autowired private ConversationService service;
  @Autowired private ModelConfigService modelConfigService;
  @Autowired private ChatMessageMapper chatMessageMapper;
  @Autowired private ConversationMapper conversationMapper;
  @Autowired private GenerationTraceMapper generationTraceMapper;
  @Autowired private KnowledgeExtractionTaskMapper extractionTaskMapper;
  @Autowired private KnowledgeCandidateMapper candidateMapper;
  @Autowired private ProcessingTaskMapper processingTaskMapper;
  @Autowired private KnowledgeDocumentMapper knowledgeItemMapper;

  @Autowired
  private knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService knowledgeBaseService;

  private Conversation createConversation(String title) {
    CreateConversationRequest req = new CreateConversationRequest();
    req.setTitle(title);
    return service.create(req);
  }

  private ChatMessage userMessage(Conversation c, long seq, String content) {
    ChatMessage m = new ChatMessage();
    m.setConversationId(c.getId());
    m.setOwnerId(1L);
    m.setRole(ChatMessage.ROLE_USER);
    m.setSequence(seq);
    m.setContent(content);
    m.setIsActive(false);
    return m;
  }

  private ChatMessage assistantMessage(
      Conversation c, long seq, long replyTo, String content, String status, boolean active) {
    ChatMessage m = new ChatMessage();
    m.setConversationId(c.getId());
    m.setOwnerId(1L);
    m.setRole(ChatMessage.ROLE_ASSISTANT);
    m.setSequence(seq);
    m.setContent(content);
    m.setReplyToMessageId(replyTo);
    m.setGenerationStatus(status);
    m.setIsActive(active);
    return m;
  }

  /** 一条 User + 一条 COMPLETED Assistant 的完整轮次，并返回 assistant 消息。 */
  private ChatMessage completedTurn(Conversation c, long seq) {
    ChatMessage user = userMessage(c, seq, "问题 " + seq);
    service.insertMessage(user);
    ChatMessage assistant =
        assistantMessage(c, seq + 1, user.getId(), "回答 " + seq, ChatMessage.COMPLETED, true);
    service.insertMessage(assistant);
    return assistant;
  }

  private ProcessingTask processingTask(String status) {
    ProcessingTask t = new ProcessingTask();
    t.setOwnerId(1L);
    t.setTaskType(ExtractionConstants.TASK_TYPE_EXTRACTION);
    t.setBusinessKey(ExtractionConstants.BUSINESS_KEY_PREFIX + System.nanoTime());
    t.setBusinessId(1L);
    t.setStatus(status);
    t.setRetryCount(0);
    t.setMaxRetries(2);
    processingTaskMapper.insert(t);
    return t;
  }

  private KnowledgeExtractionTask extractionTask(
      Conversation c, Long cutoffMessageId, Long processingTaskId) {
    KnowledgeExtractionTask t = new KnowledgeExtractionTask();
    t.setOwnerId(1L);
    t.setConversationId(c.getId());
    t.setCutoffMessageId(cutoffMessageId);
    t.setExtractionProfile(ExtractionConstants.EXTRACTION_PROFILE);
    t.setProfileVersion(ExtractionConstants.EXTRACTION_PROFILE_VERSION);
    t.setUtilityRevisionId(1L);
    t.setProcessingTaskId(processingTaskId);
    t.setInputCharCount(10);
    extractionTaskMapper.insert(t);
    return t;
  }

  private KnowledgeCandidate candidate(KnowledgeExtractionTask task, String status) {
    KnowledgeCandidate c = new KnowledgeCandidate();
    c.setOwnerId(1L);
    c.setExtractionTaskId(task.getId());
    c.setStatus(status);
    c.setAiTitle("候选标题");
    c.setAiContent("候选内容");
    c.setAiKnowledgeBaseId(null);
    c.setAiTags("[]");
    c.setDraftTitle("候选标题");
    c.setDraftContent("候选内容");
    c.setDraftKnowledgeBaseId(null);
    c.setDraftTags("[]");
    candidateMapper.insert(c);
    return c;
  }

  private GenerationTrace trace(Conversation c, ChatMessage assistant) {
    GenerationTrace t = new GenerationTrace();
    t.setAssistantMessageId(assistant.getId());
    t.setConversationId(c.getId());
    t.setOwnerId(1L);
    t.setRagStatus("ROUTED");
    generationTraceMapper.insert(t);
    return t;
  }

  private KnowledgeDocument itemFromCandidate(KnowledgeCandidate candidate, Long kbId) {
    KnowledgeDocument i = new KnowledgeDocument();
    i.setOwnerId(1L);
    i.setKbId(kbId);
    i.setSourceType(ExtractionConstants.SOURCE_AI_CONVERSATION);
    i.setTitle(candidate.getAiTitle());
    i.setContent(candidate.getAiContent());
    i.setContentVersion(1);
    i.setIndexStatus("PENDING");
    i.setDeleted(false);
    i.setRowVersion(0);
    i.setCandidateId(candidate.getId());
    knowledgeItemMapper.insert(i);
    return i;
  }

  private Long createKb() {
    knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest req =
        new knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest();
    req.setName("Conv KB " + System.nanoTime());
    return knowledgeBaseService.create(req).getId();
  }

  private long countChatMessages(Long conversationId) {
    return chatMessageMapper.selectCount(
        new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getConversationId, conversationId));
  }

  @Test
  @DisplayName("should create conversation and persist with owner id 1")
  void shouldCreateAndPersist() {
    Conversation c = createConversation("Phase 3 alpha");
    assertThat(c.getId()).isNotNull();
    assertThat(c.getTitle()).isEqualTo("Phase 3 alpha");
    assertThat(c.getDeleted()).isFalse();
    assertThat(c.getOwnerId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("should list conversations (at least the ones created in this class)")
  void shouldListNewestFirst() {
    createConversation("first");
    createConversation("second");
    createConversation("third");
    List<Conversation> list = service.listForOwner();
    assertThat(list).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  @DisplayName("should hard delete conversation")
  void shouldHardDelete() {
    Conversation c = createConversation("to-delete");
    service.hardDelete(c.getId());
    assertThatThrownBy(() -> service.getByIdAndOwner(c.getId()))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  @DisplayName("should cascade delete messages, traces, extraction tasks and candidates")
  void shouldCascadeDeleteAllConversationData() {
    Conversation c = createConversation("cascade");
    ChatMessage assistant = completedTurn(c, 1L);
    trace(c, assistant);

    ProcessingTask pt = processingTask(ProcessingConstants.STATUS_SUCCEEDED);
    KnowledgeExtractionTask et = extractionTask(c, assistant.getId(), pt.getId());
    candidate(et, ExtractionConstants.CANDIDATE_PENDING);

    service.hardDelete(c.getId());

    assertThat(countChatMessages(c.getId())).isZero();
    assertThat(
            generationTraceMapper.selectCount(
                new LambdaQueryWrapper<GenerationTrace>()
                    .eq(GenerationTrace::getConversationId, c.getId())))
        .isZero();
    assertThat(
            candidateMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeCandidate>()
                    .eq(KnowledgeCandidate::getExtractionTaskId, et.getId())))
        .isZero();
    assertThat(
            extractionTaskMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeExtractionTask>()
                    .eq(KnowledgeExtractionTask::getConversationId, c.getId())))
        .isZero();
    assertThat(
            conversationMapper.selectCount(
                new LambdaQueryWrapper<Conversation>().eq(Conversation::getId, c.getId())))
        .isZero();
  }

  @Test
  @DisplayName("should keep confirmed knowledge item and clear its candidate link on delete")
  void shouldKeepConfirmedItemAfterHardDelete() {
    Conversation c = createConversation("confirmed");
    ChatMessage assistant = completedTurn(c, 1L);
    ProcessingTask pt = processingTask(ProcessingConstants.STATUS_SUCCEEDED);
    KnowledgeExtractionTask et = extractionTask(c, assistant.getId(), pt.getId());
    KnowledgeCandidate confirmed = candidate(et, ExtractionConstants.CANDIDATE_CONFIRMED);
    KnowledgeDocument item = itemFromCandidate(confirmed, createKb());

    service.hardDelete(c.getId());

    KnowledgeDocument kept = knowledgeItemMapper.selectById(item.getId());
    assertThat(kept).isNotNull();
    assertThat(kept.getCandidateId()).isNull();
  }

  @Test
  @DisplayName("should reject hard delete when extraction task is still in progress")
  void shouldRejectHardDeleteWhenExtractionInProgress() {
    Conversation c = createConversation("extracting");
    ChatMessage assistant = completedTurn(c, 1L);
    ProcessingTask pt = processingTask(ProcessingConstants.STATUS_PROCESSING);
    extractionTask(c, assistant.getId(), pt.getId());

    assertThatThrownBy(() -> service.hardDelete(c.getId()))
        .isInstanceOf(ConversationExtractionInProgressException.class);
    // 删除被拒绝后会话仍然可用
    assertThat(service.getByIdAndOwner(c.getId())).isNotNull();
  }

  @Test
  @DisplayName("should reject delete when active generation exists")
  void shouldRejectDeleteWithActiveGeneration() {
    Conversation c = createConversation("active-delete");
    ChatMessage msg = userMessage(c, 1L, "hello");
    service.insertMessage(msg);
    // 手动把 conversation 认领为 active，模拟进行中的生成
    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), msg.getId(), java.time.Duration.ofMinutes(5)))
        .isTrue();
    assertThatThrownBy(() -> service.hardDelete(c.getId()))
        .isInstanceOf(ActiveGenerationExistsException.class);
  }

  @Test
  @DisplayName("should cancel orphaned generation before deleting conversation")
  void shouldCancelOrphanedGenerationBeforeDelete() {
    Conversation c = createConversation("orphaned-delete");
    ChatMessage msg = userMessage(c, 1L, "hello");
    msg.setRole(ChatMessage.ROLE_ASSISTANT);
    msg.setGenerationStatus(ChatMessage.GENERATING);
    msg.setIsActive(false);
    service.insertMessage(msg);
    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), msg.getId(), java.time.Duration.ofMinutes(5)))
        .isTrue();

    service.cancelOrphanedGeneration(c.getId(), msg.getId());
    ChatMessage cancelled = service.getMessage(c.getId(), msg.getId());
    assertThat(cancelled.getGenerationStatus()).isEqualTo(ChatMessage.CANCELLED);
    service.hardDelete(c.getId());

    assertThatThrownBy(() -> service.getByIdAndOwner(c.getId()))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  @DisplayName("should update title with optimistic lock and conflict on stale rowVersion")
  void shouldUpdateTitleWithRowVersion() {
    Conversation c = createConversation("old-title");
    UpdateConversationRequest req = new UpdateConversationRequest();
    req.setTitle("new-title");
    req.setRowVersion(c.getRowVersion().longValue());
    Conversation updated = service.update(c.getId(), req);
    assertThat(updated.getTitle()).isEqualTo("new-title");

    UpdateConversationRequest stale = new UpdateConversationRequest();
    stale.setTitle("stale-title");
    stale.setRowVersion(0L); // 过期版本
    assertThatThrownBy(() -> service.update(c.getId(), stale))
        .isInstanceOf(ConversationVersionConflictException.class);
  }

  @Test
  @DisplayName("should set conversation default model and clear it back to owner default")
  void shouldUpdateDefaultModelAndClear() {
    Conversation c = createConversation("model-default");
    ModelConfig model = modelConfigService.create(modelConfigRequest("conv-default-model"));
    UpdateConversationRequest set = new UpdateConversationRequest();
    set.setDefaultModelConfigId(model.getId().toString());
    service.update(c.getId(), set);
    assertThat(service.getByIdAndOwner(c.getId()).getDefaultModelConfigId())
        .isEqualTo(model.getId());

    UpdateConversationRequest clear = new UpdateConversationRequest();
    clear.setDefaultModelConfigId(""); // 空串 = 清空会话级覆盖，回到 Owner 默认
    service.update(c.getId(), clear);
    assertThat(service.getByIdAndOwner(c.getId()).getDefaultModelConfigId()).isNull();
  }

  @Test
  @DisplayName("should normalize, persist and clear conversation knowledge base bindings")
  void shouldManageKnowledgeBaseBindings() {
    CreateKnowledgeBaseRequest firstRequest = new CreateKnowledgeBaseRequest();
    firstRequest.setName("Binding KB A " + System.nanoTime());
    var first = knowledgeBaseService.create(firstRequest);
    CreateKnowledgeBaseRequest secondRequest = new CreateKnowledgeBaseRequest();
    secondRequest.setName("Binding KB B " + System.nanoTime());
    var second = knowledgeBaseService.create(secondRequest);

    CreateConversationRequest create = new CreateConversationRequest();
    create.setKnowledgeBaseIds(
        List.of(second.getId().toString(), first.getId().toString(), second.getId().toString()));
    Conversation conversation = service.create(create);
    assertThat(ConversationKnowledgeBaseIds.decode(conversation.getKnowledgeBaseIdsJson()))
        .containsExactly(first.getId(), second.getId());

    UpdateConversationRequest clear = new UpdateConversationRequest();
    clear.setKnowledgeBaseIds(List.of());
    clear.setRowVersion(conversation.getRowVersion().longValue());
    Conversation cleared = service.update(conversation.getId(), clear);
    assertThat(cleared.getKnowledgeBaseIdsJson()).isNull();
    assertThat(cleared.getRowVersion()).isEqualTo(conversation.getRowVersion() + 1);

    assertThatThrownBy(() -> service.update(conversation.getId(), clear))
        .isInstanceOf(ConversationVersionConflictException.class);
  }

  @Test
  @DisplayName("should reject a disabled knowledge base binding")
  void shouldRejectDisabledBinding() {
    CreateKnowledgeBaseRequest kbRequest = new CreateKnowledgeBaseRequest();
    kbRequest.setName("Disabled Binding " + System.nanoTime());
    var kb = knowledgeBaseService.create(kbRequest);
    knowledgeBaseService.disable(kb.getId(), kb.getRowVersion());

    CreateConversationRequest request = new CreateConversationRequest();
    request.setKnowledgeBaseIds(List.of(kb.getId().toString()));
    assertThatThrownBy(() -> service.create(request))
        .isInstanceOf(ConversationKnowledgeBaseDisabledException.class);
  }

  private CreateModelConfigRequest modelConfigRequest(String name) {
    CreateModelConfigRequest req = new CreateModelConfigRequest();
    req.setDisplayName(name);
    req.setProviderName("DeepSeek");
    req.setBaseUrl("https://api.deepseek.com");
    req.setModelName("deepseek-chat");
    req.setTemperature(0.7);
    req.setMaxOutputTokens(2048);
    req.setApiKey("sk-it-" + name);
    return req;
  }

  @Test
  @DisplayName("should paginate message history with sequence cursor ascending")
  void shouldPaginateHistoryWithCursor() {
    Conversation c = createConversation("history");
    ChatMessage m1 = userMessage(c, 1L, "one");
    service.insertMessage(m1);
    service.insertMessage(assistantMessage(c, 2L, m1.getId(), "a1", ChatMessage.COMPLETED, true));
    ChatMessage m2 = userMessage(c, 3L, "two");
    service.insertMessage(m2);
    service.insertMessage(assistantMessage(c, 4L, m2.getId(), "a2", ChatMessage.COMPLETED, true));
    ChatMessage m3 = userMessage(c, 5L, "three");
    service.insertMessage(m3);
    service.insertMessage(assistantMessage(c, 6L, m3.getId(), "a3", ChatMessage.COMPLETED, true));

    // 第一页：最新 2 条（seq 5,6）
    List<ChatMessage> page1 = service.listMessages(c.getId(), null, 2);
    assertThat(page1).hasSize(2);
    assertThat(page1.get(0).getSequence()).isEqualTo(5L);
    assertThat(page1.get(1).getSequence()).isEqualTo(6L);

    // 第二页：before=page1 第一条 sequence，返回更早 2 条（seq 3,4）
    Long before = page1.get(0).getSequence();
    List<ChatMessage> page2 = service.listMessages(c.getId(), before, 2);
    assertThat(page2).hasSize(2);
    assertThat(page2.get(0).getSequence()).isEqualTo(3L);
    assertThat(page2.get(1).getSequence()).isEqualTo(4L);

    // 第三页：before=page2 第一条 sequence，返回 seq 1,2
    List<ChatMessage> page3 = service.listMessages(c.getId(), page2.get(0).getSequence(), 2);
    assertThat(page3).hasSize(2);
    assertThat(page3.get(0).getSequence()).isEqualTo(1L);
    assertThat(page3.get(1).getSequence()).isEqualTo(2L);
  }

  @Test
  @DisplayName("should enforce clientMessageId unique across owner")
  void shouldEnforceClientMessageIdUnique() {
    Conversation c1 = createConversation("dup-1");
    Conversation c2 = createConversation("dup-2");
    ChatMessage a = userMessage(c1, 1L, "a");
    a.setClientMessageId("client-abc");
    service.insertMessage(a);

    ChatMessage b = userMessage(c2, 1L, "b");
    b.setClientMessageId("client-abc"); // 不同会话但同 owner + client id => 冲突
    assertThatThrownBy(() -> service.insertMessage(b)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("should claim active generation only once per conversation")
  void shouldClaimActiveGenerationOnce() {
    Conversation c = createConversation("claim");
    ChatMessage m1 = userMessage(c, 1L, "q1");
    ChatMessage m2 = userMessage(c, 2L, "q2");
    service.insertMessage(m1);
    service.insertMessage(m2);

    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), m1.getId(), java.time.Duration.ofMinutes(5)))
        .isTrue();
    // 第二次认领（另一消息）失败
    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), m2.getId(), java.time.Duration.ofMinutes(5)))
        .isFalse();

    service.clearActiveGeneration(c.getId());
    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), m2.getId(), java.time.Duration.ofMinutes(5)))
        .isTrue();
  }

  @Test
  @DisplayName("should enforce owner isolation on messages")
  void shouldIsolateMessagesAcrossOwners() {
    // 另一 owner 的会话消息不可见：直接查询不存在 owner 的会话应抛 404
    assertThatThrownBy(() -> service.getMessage(999999L, 1L))
        .isInstanceOf(ConversationNotFoundException.class);
  }
}
