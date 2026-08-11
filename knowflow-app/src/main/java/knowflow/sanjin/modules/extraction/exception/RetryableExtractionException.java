package knowflow.sanjin.modules.extraction.exception;

/** 可重试提取异常：网络层/临时性故障（连接超时、429、5xx 等）。Consumer 捕获后递增 retryCount 并进入下一档 TTL 重试队列。 */
public class RetryableExtractionException extends RuntimeException {

  private final String failureCode;

  public RetryableExtractionException(String failureCode, String message, Throwable cause) {
    super(message, cause);
    this.failureCode = failureCode;
  }

  public RetryableExtractionException(String failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
