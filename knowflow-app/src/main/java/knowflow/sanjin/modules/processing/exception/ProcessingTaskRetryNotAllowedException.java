package knowflow.sanjin.modules.processing.exception;

/** 手动 Retry 只在原任务 FAILED 后允许；活动状态任务不可重试。并发/重复点击会被活动状态唯一约束 （DuplicateKeyException）兜底，转为本异常。 */
public class ProcessingTaskRetryNotAllowedException extends RuntimeException {

  private final Long taskId;
  private final String status;

  public ProcessingTaskRetryNotAllowedException(Long taskId, String status) {
    super("处理任务 " + taskId + " 状态为 " + status + "，不允许重试");
    this.taskId = taskId;
    this.status = status;
  }

  public Long getTaskId() {
    return taskId;
  }

  public String getStatus() {
    return status;
  }
}
