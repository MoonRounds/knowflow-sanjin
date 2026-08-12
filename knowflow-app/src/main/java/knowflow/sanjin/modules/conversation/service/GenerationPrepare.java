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
  private final GenerationProperties properties;

  public GenerationPrepare(
      ConversationService conversationService,
      ModelConfigService modelConfigService,
      OwnerAiSettingsMapper ownerAiSettingsMapper,
      CurrentOwnerProvider currentOwnerProvider,
      GenerationProperties properties) {
    this.conversationService = conversationService;
    this.modelConfigService = modelConfigService;
    this.ownerAiSettingsMapper = ownerAiSettingsMapper;
    this.currentOwnerProvider = currentOwnerProvider;
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
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    ChatMessage latest = conversationService.lockLatestAssistantMessage(conversationId);

    if (ChatMessage.GENERATING.equals(latest.getGenerationStatus())) {
      throw new ActiveGenerationExistsException(conversationId);
    }

    ModelConfigRevision revision =
        resolveRevision(
            conversationId,
            requestedModelConfigId != null ? requestedModelConfigId : latest.getModelConfigId());
    long newSeq = latest.getSequence() + 1;

    ChatMessage newMsg = new ChatMessage();
    newMsg.setConversationId(conversationId);
    newMsg.setOwnerId(ownerId);
    newMsg.setRole(ChatMessage.ROLE_ASSISTANT);
    newMsg.setSequence(newSeq);
    newMsg.setContent("");
    newMsg.setReplyToMessageId(latest.getReplyToMessageId());
    newMsg.setGenerationStatus(ChatMessage.GENERATING);
    newMsg.setIsActive(true);
    newMsg.setModelConfigId(revision.getModelConfigId());
    newMsg.setRevisionNo(revision.getRevisionNo());
    newMsg.setModelName(revision.getModelName());
    newMsg.setProviderName(revision.getProviderName());
    newMsg.setTemperature(revision.getTemperature());
    newMsg.setMaxOutputTokens(revision.getMaxOutputTokens());
    conversationService.insertMessage(newMsg);

    if (!conversationService.tryClaimActiveGeneration(
        conversationId, newMsg.getId(), properties.getStaleTimeout())) {
      throw new ActiveGenerationExistsException(conversationId);
    }
    if (requestedModelConfigId != null) {
      conversationService.updateDefaultModelConfig(conversationId, requestedModelConfigId);
    }

    return new PreparedSend(null, newMsg, revision);
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
