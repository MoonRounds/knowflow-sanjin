package knowflow.sanjin.modules.conversation.exception;

/**
 * 会话标题任务可重试异常：临时性故障（消息读取失败、任务数据异常等）。Consumer 捕获后递增 retryCount 并进入下一档 TTL 重试队列。标题生成失败本身已内部回退，正常不抛出。
 */
public class RetryableTitleException extends RuntimeException {

  private final String failureCode;

  public RetryableTitleException(String failureCode, String message, Throwable cause) {
    super(message, cause);
    this.failureCode = failureCode;
  }

  public RetryableTitleException(String failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
