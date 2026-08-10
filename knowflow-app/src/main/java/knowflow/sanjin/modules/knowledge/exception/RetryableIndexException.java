package knowflow.sanjin.modules.knowledge.exception;

/**
 * 可重试索引异常：网络层/临时性故障（连接超时、429、5xx、Embedding/Qdrant 临时不可用）。 Consumer 捕获后递增 retryCount 并进入下一档 TTL
 * 重试队列。
 */
public class RetryableIndexException extends RuntimeException {

  private final String failureCode;

  public RetryableIndexException(String failureCode, String message, Throwable cause) {
    super(message, cause);
    this.failureCode = failureCode;
  }

  public RetryableIndexException(String failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
