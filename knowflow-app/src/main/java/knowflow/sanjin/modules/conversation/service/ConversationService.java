package knowflow.sanjin.modules.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.Instant;
import java.util.List;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.UpdateConversationRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.conversation.exception.ConversationExtractionInProgressException;
import knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException;
import knowflow.sanjin.modules.conversation.exception.MessageNotFoundException;
import knowflow.sanjin.modules.conversation.mapper.ChatMessageMapper;
import knowflow.sanjin.modules.conversation.mapper.ConversationCascadeMapper;
import knowflow.sanjin.modules.conversation.mapper.ConversationMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话与消息应用服务：Owner 隔离、软删除守卫、历史游标与 generation 辅助操作。
 *
 * <p>所有按 ID 的操作先按 {@code ownerId} 过滤（越权视为不存在）；认领 active slot 与消息状态更新
 * 用条件更新保证并发安全。消息写入事务内完成，流式阶段不持有数据库事务。
 */
@Service
public class ConversationService extends ServiceImpl<ConversationMapper, Conversation> {

  /** 占位标题：尚未生成也未手动改名（AI 生成任务以其为触发与写入前提，见 ADR 0004）。 */
  public static final String TITLE_PLACEHOLDER = "新对话";

  private final CurrentOwnerProvider currentOwnerProvider;
  private final ChatMessageMapper chatMessageMapper;
  private final ConversationMapper conversationMapper;
  private final ConversationCascadeMapper cascadeMapper;

  public ConversationService(
      CurrentOwnerProvider currentOwnerProvider,
      ChatMessageMapper chatMessageMapper,
      ConversationMapper conversationMapper,
      ConversationCascadeMapper cascadeMapper) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.chatMessageMapper = chatMessageMapper;
    this.conversationMapper = conversationMapper;
    this.cascadeMapper = cascadeMapper;
  }

  @Transactional
  public Conversation create(CreateConversationRequest request) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    Conversation c = new Conversation();
    c.setOwnerId(ownerId);
    String requestedTitle = request.getTitle() == null ? null : request.getTitle().trim();
    // 标题留空时先用「新对话」占位；首条 User Message 落库与首轮回答完成后再截断/AI 生成（见 ADR 0004）
    c.setTitle(
        requestedTitle == null || requestedTitle.isEmpty() ? TITLE_PLACEHOLDER : requestedTitle);
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

  /** 返回会话所属 owner（供 trace 落库；会话不存在时返回 0）。 */
  @Transactional(readOnly = true)
  public long ownerIdOfConversation(Long conversationId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    Conversation c =
        getOne(
            new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getOwnerId, ownerId)
                .eq(Conversation::getDeleted, false));
    return c != null ? c.getOwnerId() : 0L;
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
      if (request.getDefaultModelConfigId().isEmpty()) {
        // 空串表示清空会话级覆盖，回到 Owner 默认（动态跟随，见 DECISIONS §7）。
        // updateById 默认忽略 null 字段，必须用显式 .set 把数据库列真正置为 NULL。
        conversationMapper.update(
            null,
            new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, id)
                .eq(Conversation::getOwnerId, c.getOwnerId())
                .set(Conversation::getDefaultModelConfigId, null));
        c.setDefaultModelConfigId(null);
      } else {
        c.setDefaultModelConfigId(
            ApiValueParser.positiveId(request.getDefaultModelConfigId(), "defaultModelConfigId"));
      }
    }
    updateById(c);
    return c;
  }

  /** 仅当标题仍为「新对话」占位时写入新标题（原子条件更新）。用户手动改名或 AI 已生成后不再覆盖； 返回是否实际写入，供标题生成任务决定是否需要回退。 */
  @Transactional
  public boolean setTitleIfPlaceholder(Long conversationId, String title) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    String normalized = title == null ? null : title.trim();
    if (normalized == null || normalized.isEmpty() || normalized.length() > 200) {
      return false;
    }
    return baseMapper.update(
            null,
            new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getOwnerId, ownerId)
                .eq(Conversation::getDeleted, false)
                .eq(Conversation::getTitle, TITLE_PLACEHOLDER)
                .set(Conversation::getTitle, normalized))
        == 1;
  }

  /**
   * 硬删除会话并级联清理：删除消息、GenerationTrace、提取任务与候选，最后删除会话本身。
   *
   * <p>单事务内完成；顺序依赖外键：先删 trace（引用消息/会话）→ 候选（引用提取任务）→ 提取任务（引用会话与消息）→ 消息（自引用 reply_to 由 V10 迁移的 ON
   * DELETE CASCADE 兜底）→ 会话。已确认候选沉淀的 KnowledgeItem 依赖 V10 迁移的 ON DELETE SET NULL 保留，仅解除 candidate_id
   * 关联。删除前守卫：active 生成与 非终态提取任务 存在时拒绝，避免与生成/消费端并发竞态。
   */
  @Transactional
  public void hardDelete(Long id) {
    Conversation c = getByIdAndOwnerInternal(id);
    if (c.getActiveGenerationMessageId() != null) {
      throw new ActiveGenerationExistsException(id);
    }
    if (cascadeMapper.countActiveExtractionTasks(id) > 0) {
      throw new ConversationExtractionInProgressException(id);
    }
    cascadeMapper.deleteTraces(id);
    cascadeMapper.deleteCandidates(id);
    cascadeMapper.deleteExtractionTasks(id);
    cascadeMapper.deleteMessages(id);
    cascadeMapper.deleteConversation(id);
  }

  /** 消息历史游标分页：before 是会话内 sequence；先倒序取 limit 条，再反转为 sequence 正序。 */
  @Transactional(readOnly = true)
  public List<ChatMessage> listMessages(Long conversationId, Long before, int limit) {
    getByIdAndOwnerInternal(conversationId);
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    // Controller 可多取一条判断 hasMore；正式页大小仍上限 100。
    int safeLimit = Math.max(1, Math.min(limit, 101));
    LambdaQueryWrapper<ChatMessage> wrapper =
        new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getOwnerId, ownerId);
    if (before != null) {
      wrapper.lt(ChatMessage::getSequence, before);
    }
    List<ChatMessage> page =
        chatMessageMapper.selectList(
            wrapper.orderByDesc(ChatMessage::getSequence).last("LIMIT " + safeLimit));
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
   * 覆盖式重新生成：按 id 原位更新 assistant 消息（同 id 同 sequence 写回新流结果），而不是追加新消息。
   *
   * <p>必须用显式 {@code .set} 写入每个字段：MyBatis-Plus 默认 NOT_NULL 更新策略会让 {@code updateById} 跳过 null
   * 字段，导致上一轮残留的 errorCode/ragStatus/usage 等终态元数据无法清空。同时显式刷新 updatedAt（strictUpdateFill 只在字段为 null
   * 时填充，重新生成的消息从 DB 读出的 updatedAt 非空不会被刷新）， 否则 tryClaimActiveGeneration 的 stale
   * 启发式会把刚认领的重新生成误判为孤儿并发抢占。
   */
  @Transactional
  public void updateMessage(ChatMessage message) {
    chatMessageMapper.update(
        null,
        new LambdaUpdateWrapper<ChatMessage>()
            .eq(ChatMessage::getId, message.getId())
            .eq(ChatMessage::getOwnerId, message.getOwnerId())
            .set(ChatMessage::getContent, message.getContent())
            .set(ChatMessage::getGenerationStatus, message.getGenerationStatus())
            .set(ChatMessage::getIsActive, message.getIsActive())
            .set(ChatMessage::getModelConfigId, message.getModelConfigId())
            .set(ChatMessage::getRevisionNo, message.getRevisionNo())
            .set(ChatMessage::getModelName, message.getModelName())
            .set(ChatMessage::getProviderName, message.getProviderName())
            .set(ChatMessage::getTemperature, message.getTemperature())
            .set(ChatMessage::getMaxOutputTokens, message.getMaxOutputTokens())
            .set(ChatMessage::getErrorCode, message.getErrorCode())
            .set(ChatMessage::getRagStatus, message.getRagStatus())
            .set(ChatMessage::getUsagePromptTokens, message.getUsagePromptTokens())
            .set(ChatMessage::getUsageCompletionTokens, message.getUsageCompletionTokens())
            .set(ChatMessage::getUsageTotalTokens, message.getUsageTotalTokens())
            .set(ChatMessage::getUpdatedAt, Instant.now()));
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

  /**
   * 收口本进程中已不存在执行任务的孤儿 generation，并仅在 active slot 仍指向该消息时释放占用。
   *
   * <p>正常运行中的取消仍由 {@link GenerationFinalizer} 保存 partial content 与 trace；本方法只处理应用重启或执行线程异常退出后的遗留状态。
   */
  @Transactional
  public void cancelOrphanedGeneration(Long conversationId, Long assistantMessageId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    chatMessageMapper.update(
        null,
        new LambdaUpdateWrapper<ChatMessage>()
            .eq(ChatMessage::getId, assistantMessageId)
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getOwnerId, ownerId)
            .eq(ChatMessage::getGenerationStatus, ChatMessage.GENERATING)
            .set(ChatMessage::getGenerationStatus, ChatMessage.CANCELLED)
            .set(ChatMessage::getIsActive, false)
            .set(ChatMessage::getErrorCode, null));
    baseMapper.update(
        null,
        new LambdaUpdateWrapper<Conversation>()
            .eq(Conversation::getId, conversationId)
            .eq(Conversation::getOwnerId, ownerId)
            .eq(Conversation::getDeleted, false)
            .eq(Conversation::getActiveGenerationMessageId, assistantMessageId)
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
      Long conversationId, Long messageId, String content, String errorCode, String ragStatus) {
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
            .set(ChatMessage::getErrorCode, errorCode)
            .set(ChatMessage::getRagStatus, ragStatus));
  }

  @Transactional
  public void markMessageCancelled(
      Long conversationId, Long messageId, String content, String ragStatus) {
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
            .set(ChatMessage::getErrorCode, null)
            .set(ChatMessage::getRagStatus, ragStatus));
  }

  /** 写成功状态并切换 active：原子地把同一轮的旧 active attempt 置为非 active，再激活本 attempt。 */
  @Transactional
  public void completeGeneration(
      long conversationId,
      long assistantMessageId,
      String content,
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      boolean makeActive,
      String ragStatus) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    ChatMessage self = chatMessageMapper.selectById(assistantMessageId);
    if (makeActive) {
      chatMessageMapper.update(
          null,
          new LambdaUpdateWrapper<ChatMessage>()
              .eq(ChatMessage::getConversationId, conversationId)
              .eq(ChatMessage::getOwnerId, ownerId)
              .eq(
                  ChatMessage::getReplyToMessageId,
                  self != null ? self.getReplyToMessageId() : null)
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
            .set(ChatMessage::getErrorCode, null)
            .set(ChatMessage::getRagStatus, ragStatus));
  }

  /** 会话内最后一个消息 id（自增主键单调）；无消息返回 null。 */
  @Transactional(readOnly = true)
  public Long lastMessageId(Long conversationId, long ownerId) {
    ChatMessage last =
        chatMessageMapper.selectOne(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getOwnerId, ownerId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
    return last != null ? last.getId() : null;
  }

  /**
   * 截至 cutoffMessageId 之前的全部完整 active Turns（不过 LIMIT）：仅 COMPLETED 且 isActive 的 Assistant 及其 User
   * 消息，按 id 正序。供提取任务确定输入范围（DECISIONS §11：范围固定为触发时 cutoff，后续新增消息不纳入）。
   */
  @Transactional(readOnly = true)
  public List<ChatMessage> loadAllTurnsUpTo(
      Long conversationId, long ownerId, Long cutoffMessageId) {
    return loadCompletedTurns(conversationId, cutoffMessageId, null);
  }

  /** 最近上下文：最近 N 个完整 active Turn（仅 COMPLETED 且 isActive=1 的 Assistant 及其 User）。 */
  @Transactional(readOnly = true)
  public List<ChatMessage> loadRecentContext(Long conversationId, int turns) {
    return loadCompletedTurns(conversationId, null, turns);
  }

  /**
   * 公共实现：读取完整 active Turns。{@code cutoffMessageId} 非空时只取 id 不大于 cutoff 的 Assistant（提取固定范围）；{@code
   * limitTurns} 非空时只取最近 N 个（Memory 上下文）。两者互斥；任一路径都先校验会话存在（软删会话不提供上下文）。
   */
  private List<ChatMessage> loadCompletedTurns(
      Long conversationId, Long cutoffMessageId, Integer limitTurns) {
    getByIdAndOwnerInternal(conversationId);
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    LambdaQueryWrapper<ChatMessage> wrapper =
        new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, conversationId)
            .eq(ChatMessage::getOwnerId, ownerId)
            .eq(ChatMessage::getRole, ChatMessage.ROLE_ASSISTANT)
            .eq(ChatMessage::getGenerationStatus, ChatMessage.COMPLETED)
            .eq(ChatMessage::getIsActive, true);
    boolean limited = limitTurns != null;
    if (cutoffMessageId != null) {
      wrapper.le(ChatMessage::getId, cutoffMessageId);
    }
    if (limited) {
      int safe = Math.max(1, Math.min(limitTurns, 100));
      wrapper.orderByDesc(ChatMessage::getId).last("LIMIT " + safe);
    } else {
      wrapper.orderByAsc(ChatMessage::getId);
    }
    List<ChatMessage> activeAssistant = chatMessageMapper.selectList(wrapper);
    if (activeAssistant.isEmpty()) {
      return List.of();
    }
    if (limited) {
      // 最近 N 个取回后转正序（与调用方历史展示一致）
      java.util.Collections.reverse(activeAssistant);
    }
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
