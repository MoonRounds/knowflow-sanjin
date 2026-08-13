package knowflow.sanjin.modules.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import knowflow.sanjin.modules.conversation.dto.SendMessageRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.conversation.exception.NoDefaultModelConfigException;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.entity.OwnerAiSettings;
import knowflow.sanjin.modules.modelconfig.mapper.OwnerAiSettingsMapper;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generation 的 Tx1：在单个事务内行锁 conversation、创建 User/Assistant 消息、认领 active slot。
 *
 * <p>提交后单 active Generation 得到保证。重复 clientMessageId 视为幂等，返回已有的 assistant 消息 （可选，作为响应提示），不创建新流。
 */
@Service
public class GenerationPrepare {

  private final ConversationService conversationService;
  private final ModelConfigService modelConfigService;
  private final OwnerAiSettingsMapper ownerAiSettingsMapper;
  private final CurrentOwnerProvider currentOwnerProvider;
  private final GenerationFinalizer finalizer;
  private final GenerationProperties properties;

  public GenerationPrepare(
      ConversationService conversationService,
      ModelConfigService modelConfigService,
      OwnerAiSettingsMapper ownerAiSettingsMapper,
      CurrentOwnerProvider currentOwnerProvider,
      GenerationFinalizer finalizer,
      GenerationProperties properties) {
    this.conversationService = conversationService;
    this.modelConfigService = modelConfigService;
    this.ownerAiSettingsMapper = ownerAiSettingsMapper;
    this.currentOwnerProvider = currentOwnerProvider;
    this.finalizer = finalizer;
    this.properties = properties;
  }

  /**
   * 事务内准备一次发送。返回 PreparedSend（user 消息 + assistant 消息 + revision）。 重复 clientMessageId 时抛出
   * DuplicateKeyException。
   */
  @Transactional
  public PreparedSend prepareSend(
      Long conversationId, SendMessageRequest request, Long requestedModelConfigId) {
    conversationService.lockConversation(conversationId); // 串行化
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    ChatMessage dup = findByClientMessageId(conversationId, ownerId, request.getClientMessageId());
    if (dup != null) {
      throw new DuplicateKeyException("clientMessageId already used");
    }

    ModelConfigRevision revision = resolveRevision(conversationId, requestedModelConfigId);
    Conversation convo = conversationService.getByIdAndOwner(conversationId);
    long lastSeq = conversationService.lastSequence(conversationId, ownerId);
    long userSeq = lastSeq + 1;
    long assistantSeq = userSeq + 1;

    ChatMessage userMsg = new ChatMessage();
    userMsg.setConversationId(conversationId);
    userMsg.setOwnerId(ownerId);
    userMsg.setRole(ChatMessage.ROLE_USER);
    userMsg.setSequence(userSeq);
    userMsg.setContent(request.getContent());
    userMsg.setClientMessageId(request.getClientMessageId());
    userMsg.setIsActive(false);
    conversationService.insertMessage(userMsg);

    ChatMessage assistantMsg = new ChatMessage();
    assistantMsg.setConversationId(conversationId);
    assistantMsg.setOwnerId(ownerId);
    assistantMsg.setRole(ChatMessage.ROLE_ASSISTANT);
    assistantMsg.setSequence(assistantSeq);
    assistantMsg.setContent("");
    assistantMsg.setReplyToMessageId(userMsg.getId());
    assistantMsg.setGenerationStatus(ChatMessage.GENERATING);
    assistantMsg.setIsActive(true);
    assistantMsg.setModelConfigId(revision.getModelConfigId());
    assistantMsg.setRevisionNo(revision.getRevisionNo());
    assistantMsg.setModelName(revision.getModelName());
    assistantMsg.setProviderName(revision.getProviderName());
    assistantMsg.setTemperature(revision.getTemperature());
    assistantMsg.setMaxOutputTokens(revision.getMaxOutputTokens());
    conversationService.insertMessage(assistantMsg);

    // 认领 active slot：条件更新必须成功，否则抛并发生成冲突
    if (!conversationService.tryClaimActiveGeneration(
        conversationId, assistantMsg.getId(), properties.getStaleTimeout())) {
      throw new ActiveGenerationExistsException(conversationId);
    }

    // 请求指定模型时更新 Conversation default
    if (requestedModelConfigId != null
        && !requestedModelConfigId.equals(convo.getDefaultModelConfigId())) {
      conversationService.updateDefaultModelConfig(conversationId, requestedModelConfigId);
    }

    return new PreparedSend(userMsg, assistantMsg, revision);
  }

  @Transactional
  public PreparedSend prepareRegenerate(Long conversationId, Long requestedModelConfigId) {
    conversationService.lockConversation(conversationId);
    ChatMessage latest = conversationService.lockLatestAssistantMessage(conversationId);

    if (ChatMessage.GENERATING.equals(latest.getGenerationStatus())) {
      throw new ActiveGenerationExistsException(conversationId);
    }

    ModelConfigRevision revision =
        resolveRevision(
            conversationId,
            requestedModelConfigId != null ? requestedModelConfigId : latest.getModelConfigId());
    // 覆盖式重新生成：复用最新 assistant 消息（同 id 同 sequence 原位），清空旧内容后由新流写回。
    // 追加新消息会让旧内容残留在对话里，"重新生成=原位置替换"的语义无法成立。
    latest.setContent("");
    latest.setGenerationStatus(ChatMessage.GENERATING);
    latest.setIsActive(true);
    latest.setErrorCode(null);
    latest.setRagStatus(null);
    latest.setUsagePromptTokens(null);
    latest.setUsageCompletionTokens(null);
    latest.setUsageTotalTokens(null);
    latest.setModelConfigId(revision.getModelConfigId());
    latest.setRevisionNo(revision.getRevisionNo());
    latest.setModelName(revision.getModelName());
    latest.setProviderName(revision.getProviderName());
    latest.setTemperature(revision.getTemperature());
    latest.setMaxOutputTokens(revision.getMaxOutputTokens());
    conversationService.updateMessage(latest);

    if (!conversationService.tryClaimActiveGeneration(
        conversationId, latest.getId(), properties.getStaleTimeout())) {
      throw new ActiveGenerationExistsException(conversationId);
    }
    // 同 id 复用：清除旧流的终态标记，否则 finalizer 的幂等去重会拦截新流完成，active slot 永不释放。
    finalizer.reset(latest.getId());
    if (requestedModelConfigId != null) {
      conversationService.updateDefaultModelConfig(conversationId, requestedModelConfigId);
    }

    return new PreparedSend(null, latest, revision);
  }

  // ---- helpers ----

  private ChatMessage findByClientMessageId(
      long conversationId, long ownerId, String clientMessageId) {
    return conversationService.findMessageByClientId(conversationId, ownerId, clientMessageId);
  }

  /** 模型解析：请求指定 > Conversation default > Owner default。均无则抛 NO_DEFAULT_MODEL_CONFIG。 */
  private ModelConfigRevision resolveRevision(Long conversationId, Long requestedConfigId) {
    Long configId = requestedConfigId;
    if (configId == null) {
      Conversation convo = conversationService.getByIdAndOwner(conversationId);
      configId = convo.getDefaultModelConfigId();
    }
    if (configId == null) {
      OwnerAiSettings settings =
          ownerAiSettingsMapper.selectOne(
              new LambdaQueryWrapper<OwnerAiSettings>()
                  .eq(OwnerAiSettings::getOwnerId, currentOwnerProvider.getCurrentOwnerId()));
      if (settings != null && settings.getDefaultChatModelConfigId() != null) {
        configId = settings.getDefaultChatModelConfigId();
      }
    }
    if (configId == null) {
      throw new NoDefaultModelConfigException(conversationId);
    }
    return modelConfigService.resolveRevisionForGeneration(configId);
  }

  /** Tx1 的结果：已持久化的消息与锁定 Revision。 */
  public record PreparedSend(
      ChatMessage userMessage, ChatMessage assistantMessage, ModelConfigRevision revision) {}
}
