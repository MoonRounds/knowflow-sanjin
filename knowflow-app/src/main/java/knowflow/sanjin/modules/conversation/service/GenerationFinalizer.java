package knowflow.sanjin.modules.conversation.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Generation 生命周期终结器：负责把最终状态写库并释放 active slot。
 *
 * <p>所有成功/失败/取消/断连路径都必须经过它，且多次调用是幂等的（内部按 messageId 去重）。
 */
@Component
public class GenerationFinalizer {

  private final ConversationService conversationService;
  private final ConcurrentHashMap.KeySetView<Long, Boolean> finalized =
      ConcurrentHashMap.newKeySet();

  public GenerationFinalizer(ConversationService conversationService) {
    this.conversationService = conversationService;
  }

  /** 写成功状态；若本 attempt 需要成为 active，则原子切换并保证旧 active 不再 active。 */
  public void complete(
      long conversationId,
      long assistantMessageId,
      String content,
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      boolean makeActive) {
    if (!finalized.add(assistantMessageId)) {
      return;
    }
    try {
      conversationService.completeGeneration(
          conversationId,
          assistantMessageId,
          content,
          promptTokens,
          completionTokens,
          totalTokens,
          makeActive);
    } finally {
      conversationService.clearActiveGeneration(conversationId);
    }
  }

  /** 写失败状态并释放 slot。 */
  public void fail(long conversationId, long assistantMessageId, String content, String errorCode) {
    if (!finalized.add(assistantMessageId)) {
      return;
    }
    try {
      conversationService.markMessageFailed(conversationId, assistantMessageId, content, errorCode);
    } finally {
      conversationService.clearActiveGeneration(conversationId);
    }
  }

  /** 写取消状态并释放 slot。 */
  public void cancel(long conversationId, long assistantMessageId, String content) {
    if (!finalized.add(assistantMessageId)) {
      return;
    }
    try {
      conversationService.markMessageCancelled(conversationId, assistantMessageId, content);
    } finally {
      conversationService.clearActiveGeneration(conversationId);
    }
  }

  /** 仅释放 active slot，不改消息状态（如超时中断、断连清理的兜底）。 */
  public void releaseSlot(long conversationId) {
    conversationService.clearActiveGeneration(conversationId);
  }
}
