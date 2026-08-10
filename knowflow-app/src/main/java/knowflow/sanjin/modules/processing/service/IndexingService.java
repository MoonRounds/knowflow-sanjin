package knowflow.sanjin.modules.processing.service;

import knowflow.sanjin.modules.processing.entity.ProcessingTask;

/**
 * 索引任务执行器：按任务类型与 payload 执行实际索引。
 *
 * <p>成功返回；可重试故障抛 {@code RetryableIndexException}，不可重试抛 {@code TerminalIndexException}， 由 {@link
 * IndexTaskConsumer} 统一转换为任务状态与重试/DLQ 行为。
 */
public interface IndexingService {

  void execute(ProcessingTask task);
}
