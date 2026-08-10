package knowflow.sanjin.modules.conversation.service;

import java.util.concurrent.ConcurrentHashMap;
import knowflow.sanjin.modules.conversation.memory.MemoryService;
import org.springframework.stereotype.Component;

/**
 * Generation 生命周期终结器：负责把最终状态与 RAG trace 写库并释放 active slot。
 *
 * <p>所有成功/失败/取消/断连路径都必须经过它，且多次调用是幂等的（内部按 messageId 去重）。消息状态与 RAG trace 在同一事务提交 （由 {@link
 * GenerationTraceService} 编排），保证「消息 COMPLETED 必有 trace」。成功路径在 MySQL 提交（Tx2）后刷新 Redis Memory
 * 投影；Redis 写失败不影响 MySQL 已提交结果。
 */
@Component
public class GenerationFinalizer {

  private final GenerationTraceService traceService;
  private final ConversationService conversationService;
  private final MemoryService memoryService;
  private final ConcurrentHashMap.KeySetView<Long, Boolean> finalized =
      ConcurrentHashMap.newKeySet();

  public GenerationFinalizer(
      GenerationTraceService traceService,
      ConversationService conversationService,
      MemoryService memoryService) {
    this.traceService = traceService;
    this.conversationService = conversationService;
    this.memoryService = memoryService;
  }

  /** 写成功状态；若本 attempt 需要成为 active，则原子切换并保证旧 active 不再 active。 */
  public void complete(
      long conversationId,
      long assistantMessageId,
      String content,
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      boolean makeActive,
      GenerationTraceSnapshot traceSnapshot) {
    if (!finalized.add(assistantMessageId)) {
      return;
    }
    try {
      traceService.completeWithTrace(
          conversationId,
          assistantMessageId,
          content,
          promptTokens,
          completionTokens,
          totalTokens,
          makeActive,
          traceSnapshot);
    } finally {
      conversationService.clearActiveGeneration(conversationId);
    }
    // Tx2 提交后刷新投影：新完成的 active Turn 进入 Memory；失败则旧投影保持（下次读时从 MySQL 重建）
    memoryService.rebuild(conversationId);
  }

  /** 写失败状态并释放 slot。 */
  public void fail(
      long conversationId,
      long assistantMessageId,
      String content,
      String errorCode,
      GenerationTraceSnapshot traceSnapshot) {
    if (!finalized.add(assistantMessageId)) {
      return;
    }
    try {
      traceService.failWithTrace(
          conversationId, assistantMessageId, content, errorCode, traceSnapshot);
    } finally {
      conversationService.clearActiveGeneration(conversationId);
    }
  }

  /** 写取消状态并释放 slot。 */
  public void cancel(
      long conversationId,
      long assistantMessageId,
      String content,
      GenerationTraceSnapshot traceSnapshot) {
    if (!finalized.add(assistantMessageId)) {
      return;
    }
    try {
      traceService.cancelWithTrace(conversationId, assistantMessageId, content, traceSnapshot);
    } finally {
      conversationService.clearActiveGeneration(conversationId);
    }
  }

  /** 仅释放 active slot，不改消息状态（如超时中断、断连清理的兜底）。 */
  public void releaseSlot(long conversationId) {
    conversationService.clearActiveGeneration(conversationId);
  }
}
