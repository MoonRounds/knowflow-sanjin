package knowflow.sanjin.modules.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.UpdateConversationRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException;
import knowflow.sanjin.modules.conversation.exception.MessageNotFoundException;
import knowflow.sanjin.modules.conversation.mapper.ChatMessageMapper;
import knowflow.sanjin.modules.conversation.mapper.ConversationMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService extends ServiceImpl<ConversationMapper, Conversation> {

  private final CurrentOwnerProvider currentOwnerProvider;
  private final ChatMessageMapper chatMessageMapper;
  private final ConversationMapper conversationMapper;

  public ConversationService(
      CurrentOwnerProvider currentOwnerProvider,
      ChatMessageMapper chatMessageMapper,
      ConversationMapper conversationMapper) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.chatMessageMapper = chatMessageMapper;
    this.conversationMapper = conversationMapper;
  }

  @Transactional
  public Conversation create(CreateConversationRequest request) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    Conversation c = new Conversation();
    c.setOwnerId(ownerId);
    c.setTitle(request.getTitle().trim());
    c.setDeleted(false);
    c.setRowVersion(0);
    save(c);
    return c;
  }

  @Transactional(readOnly = true)
  public List<Conversation> listForOwner() {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return list(
        new LambdaQueryWrapper<Conversation>()
            .eq(Conversation::getOwnerId, ownerId)
            .eq(Conversation::getDeleted, false)
            .orderByDesc(Conversation::getUpdatedAt));
  }

  @Transactional(readOnly = true)
  public Conversation getByIdAndOwner(Long id) {
    return getByIdAndOwnerInternal(id);
  }

  @Transactional
  public Conversation update(Long id, UpdateConversationRequest request) {
    Conversation c = getByIdAndOwnerInternal(id);
    if (request.getRowVersion() != null
        && c.getRowVersion() != null
        && request.getRowVersion().longValue() != c.getRowVersion().longValue()) {
      throw new OptimisticLockingFailureException("Conversation version conflict: id=" + id);
    }
    if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
      c.setTitle(request.getTitle().trim());
    }
    if (request.getDefaultModelConfigId() != null) {
      c.setDefaultModelConfigId(request.getDefaultModelConfigId());
    }
    updateById(c);
    return c;
  }

  @Transactional
  public void softDelete(Long id) {
    Conversation c = getByIdAndOwnerInternal(id);
    if (c.getActiveGenerationMessageId() != null) {
      throw new ActiveGenerationExistsException(id);
    }
    c.setDeleted(true);
    updateById(c);
  }

  /**
   * 消息历史游标分页：返回 before 指向消息之前的更早消息，或最新消息；均按 id 正序返回。
   *
   * <p>实现：先按 id 倒序取 limit 条，再反转得到正序。自增主键 id 单调递增且充当游标。
   */
  @Transactional(readOnly = true)
  public List<ChatMessage> listMessages(Long conversationId, Long before, int limit) {
    getByIdAndOwnerInternal(conversationId);
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    int safeLimit = Math.max(1, Math.min(limit, 100));
    LambdaQueryWrapper<ChatMessage> wrapper =
        new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getOwnerId, ownerId);
    if (before != null) {
      wrapper.lt(ChatMessage::getId, before);
    }
    List<ChatMessage> page =
        chatMessageMapper.selectList(
            wrapper.orderByDesc(ChatMessage::getId).last("LIMIT " + safeLimit));
    java.util.Collections.reverse(page);
    return page;
  }

  @Transactional(readOnly = true)
  public ChatMessage getMessage(Long conversationId, Long messageId) {
    getByIdAndOwnerInternal(conversationId);
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    ChatMessage msg =
        chatMessageMapper.selectOne(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getId, messageId)
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getOwnerId, ownerId));
    if (msg == null) {
      throw new MessageNotFoundException(conversationId, messageId);
    }
    return msg;
  }

  /** 带行锁读取 conversation（供生成事务串行化 active slot 与 sequence）。 */
  @Transactional
  public Conversation lockConversation(Long conversationId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    Conversation c = conversationMapper.selectConversationForUpdate(conversationId, ownerId);
    if (c == null) {
      throw new ConversationNotFoundException(conversationId);
    }
    return c;
  }

  /** 最新一条 assistant 消息（regenerate 的锁定目标）。 */
  @Transactional
  public ChatMessage lockLatestAssistantMessage(Long conversationId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    ChatMessage msg =
        chatMessageMapper.selectOne(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getOwnerId, ownerId)
                .eq(ChatMessage::getRole, ChatMessage.ROLE_ASSISTANT)
                .orderByDesc(ChatMessage::getSequence)
                .last("LIMIT 1"));
    if (msg == null) {
      throw new MessageNotFoundException(conversationId, null);
    }
    return msg;
  }

  /** 按 clientMessageId 查找消息（幂等去重），无则返回 null。 */
  @Transactional(readOnly = true)
  public ChatMessage findMessageByClientId(
      long conversationId, long ownerId, String clientMessageId) {
    return chatMessageMapper.selectOne(
        new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getOwnerId, ownerId)
            .eq(ChatMessage::getClientMessageId, clientMessageId)
            .last("LIMIT 1"));
  }

  /** 会话内最大 sequence；无则 0。 */
  @Transactional(readOnly = true)
  public long lastSequence(long conversationId, long ownerId) {
    ChatMessage last =
        chatMessageMapper.selectOne(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getOwnerId, ownerId)
                .orderByDesc(ChatMessage::getSequence)
                .last("LIMIT 1"));
    return last != null ? last.getSequence() : 0L;
  }

  /** 更新 Conversation 默认模型（不改变已存在的 active 生成）。 */
  @Transactional
  public void updateDefaultModelConfig(Long conversationId, Long modelConfigId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    update(
        new LambdaUpdateWrapper<Conversation>()
            .eq(Conversation::getId, conversationId)
            .eq(Conversation::getOwnerId, ownerId)
            .set(Conversation::getDefaultModelConfigId, modelConfigId));
  }

  /** 供 Generation 在事务内写入：插入消息，重复 clientMessageId 时抛出 DuplicateKeyException。 */
  @Transactional
  public ChatMessage insertMessage(ChatMessage message) {
    try {
      chatMessageMapper.insert(message);
      return message;
    } catch (DuplicateKeyException e) {
      throw e;
    }
  }

  /**
   * 事务内通过 id + 当前 active 值条件更新认领 active slot。
   *
   * <p>若认领失败（slot 被占）且占用者已超过 {@code staleTimeout}（例如 executor 线程异常退出导致 finalizer 未运行）， 则把该消息标记
   * FAILED 并释放 slot 后重试，避免 active generation 永久卡死。成功即代表该次认领有效。
   */
  @Transactional
  public boolean tryClaimActiveGeneration(
      Long conversationId, Long messageId, java.time.Duration staleTimeout) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    int updated =
        baseMapper.update(
            null,
            new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getOwnerId, ownerId)
                .isNull(Conversation::getActiveGenerationMessageId)
                .set(Conversation::getActiveGenerationMessageId, messageId));
    if (updated == 1) {
      return true;
    }

    // 认领失败：检查占用者是否 stale
    Conversation c = conversationMapper.selectConversationForUpdate(conversationId, ownerId);
    if (c == null || c.getActiveGenerationMessageId() == null) {
      // 并发下 slot 刚被释放，重试一次
      return retryClaim(conversationId, messageId, ownerId);
    }
    ChatMessage occupant = chatMessageMapper.selectById(c.getActiveGenerationMessageId());
    if (occupant == null
        || ChatMessage.GENERATING.equals(occupant.getGenerationStatus())
            && occupant.getUpdatedAt() != null
            && occupant.getUpdatedAt().isBefore(java.time.Instant.now().minus(staleTimeout))) {
      // 占用者已 stale：标记 FAILED + 释放 slot，再认领
      chatMessageMapper.update(
          null,
          new LambdaUpdateWrapper<ChatMessage>()
              .eq(ChatMessage::getId, c.getActiveGenerationMessageId())
              .eq(ChatMessage::getOwnerId, ownerId)
              .set(ChatMessage::getGenerationStatus, ChatMessage.FAILED)
              .set(ChatMessage::getIsActive, false)
              .set(ChatMessage::getErrorCode, ErrorCode.MODEL_CALL_TIMEOUT));
      clearActiveGeneration(conversationId);
      return retryClaim(conversationId, messageId, ownerId);
    }
    return false;
  }

  private boolean retryClaim(Long conversationId, Long messageId, long ownerId) {
    int updated =
        baseMapper.update(
            null,
            new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getOwnerId, ownerId)
                .isNull(Conversation::getActiveGenerationMessageId)
                .set(Conversation::getActiveGenerationMessageId, messageId));
    return updated == 1;
  }

  @Transactional
  public void clearActiveGeneration(Long conversationId) {
    update(
        new LambdaUpdateWrapper<Conversation>()
            .eq(Conversation::getId, conversationId)
            .eq(Conversation::getOwnerId, currentOwnerProvider.getCurrentOwnerId())
            .set(Conversation::getActiveGenerationMessageId, null));
  }

  /** 查询本 attempt 是否仍持有 conversation 的 active slot（用于 completed 事件 active 标记）。 */
  @Transactional(readOnly = true)
  public boolean isActiveGeneration(Long conversationId, Long assistantMessageId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    Conversation c =
        getOne(
            new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getOwnerId, ownerId)
                .eq(Conversation::getDeleted, false));
    return c != null
        && c.getActiveGenerationMessageId() != null
        && c.getActiveGenerationMessageId().equals(assistantMessageId);
  }

  @Transactional
  public void markMessageFailed(
      Long conversationId, Long messageId, String content, String errorCode) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    chatMessageMapper.update(
        null,
        new LambdaUpdateWrapper<ChatMessage>()
            .eq(ChatMessage::getId, messageId)
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getOwnerId, ownerId)
            .set(ChatMessage::getContent, content)
            .set(ChatMessage::getGenerationStatus, ChatMessage.FAILED)
            .set(ChatMessage::getIsActive, false)
            .set(ChatMessage::getErrorCode, errorCode));
  }

  @Transactional
  public void markMessageCancelled(Long conversationId, Long messageId, String content) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    chatMessageMapper.update(
        null,
        new LambdaUpdateWrapper<ChatMessage>()
            .eq(ChatMessage::getId, messageId)
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getOwnerId, ownerId)
            .set(ChatMessage::getContent, content)
            .set(ChatMessage::getGenerationStatus, ChatMessage.CANCELLED)
            .set(ChatMessage::getIsActive, false)
            .set(ChatMessage::getErrorCode, null));
  }

  /** 写成功状态并切换 active：原子地把旧 active attempt 置为非 active，再激活本 attempt。 */
  @Transactional
  public void completeGeneration(
      long conversationId,
      long assistantMessageId,
      String content,
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      boolean makeActive) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    if (makeActive) {
      chatMessageMapper.update(
          null,
          new LambdaUpdateWrapper<ChatMessage>()
              .eq(ChatMessage::getConversationId, conversationId)
              .eq(ChatMessage::getOwnerId, ownerId)
              .eq(ChatMessage::getIsActive, true)
              .set(ChatMessage::getIsActive, false));
    }
    chatMessageMapper.update(
        null,
        new LambdaUpdateWrapper<ChatMessage>()
            .eq(ChatMessage::getId, assistantMessageId)
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getOwnerId, ownerId)
            .set(ChatMessage::getContent, content)
            .set(ChatMessage::getGenerationStatus, ChatMessage.COMPLETED)
            .set(ChatMessage::getIsActive, makeActive)
            .set(ChatMessage::getUsagePromptTokens, promptTokens)
            .set(ChatMessage::getUsageCompletionTokens, completionTokens)
            .set(ChatMessage::getUsageTotalTokens, totalTokens)
            .set(ChatMessage::getErrorCode, null));
  }

  /** 最近上下文：最近 N 个完整 active Turn（仅 COMPLETED 且 isActive=1 的 Assistant 及其 User）。 */
  @Transactional(readOnly = true)
  public List<ChatMessage> loadRecentContext(Long conversationId, int turns) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    int safeTurns = Math.max(1, Math.min(turns, 100));
    List<ChatMessage> activeAssistant =
        chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getOwnerId, ownerId)
                .eq(ChatMessage::getRole, ChatMessage.ROLE_ASSISTANT)
                .eq(ChatMessage::getGenerationStatus, ChatMessage.COMPLETED)
                .eq(ChatMessage::getIsActive, true)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + safeTurns));
    if (activeAssistant.isEmpty()) {
      return List.of();
    }
    // 正序
    java.util.Collections.reverse(activeAssistant);
    List<Long> userIds =
        activeAssistant.stream()
            .map(ChatMessage::getReplyToMessageId)
            .filter(java.util.Objects::nonNull)
            .toList();
    if (userIds.isEmpty()) {
      return activeAssistant;
    }
    List<ChatMessage> users =
        chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .in(ChatMessage::getId, userIds)
                .eq(ChatMessage::getOwnerId, ownerId));
    // 按 id 升序把 user 穿插回 assistant 之前
    List<ChatMessage> all = new java.util.ArrayList<>(activeAssistant);
    all.addAll(users);
    all.sort(java.util.Comparator.comparing(ChatMessage::getId));
    return all;
  }

  private Conversation getByIdAndOwnerInternal(Long id) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    Conversation c =
        getOne(
            new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, id)
                .eq(Conversation::getOwnerId, ownerId)
                .eq(Conversation::getDeleted, false));
    if (c == null) {
      throw new ConversationNotFoundException(id);
    }
    return c;
  }
}
