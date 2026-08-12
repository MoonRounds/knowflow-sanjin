package knowflow.sanjin.modules.processing.service;

import knowflow.sanjin.modules.processing.entity.ProcessingTask;

/** 各业务模块为 Processing 手动重试/租约恢复提供的领域状态回写边界。 */
public interface ProcessingTaskDomainRecovery {

  boolean supports(ProcessingTask task);

  default String queueBase() {
    return null;
  }

  default void markProcessing(ProcessingTask task) {}

  default void markRetryPending(ProcessingTask task) {
    prepareForRepublish(task);
  }

  default void markFailed(ProcessingTask task, String failureCode, String lastError) {}

  void prepareForRepublish(ProcessingTask task);

  default void prepareForRetry(ProcessingTask original, ProcessingTask retry) {
    prepareForRepublish(original);
  }
}
