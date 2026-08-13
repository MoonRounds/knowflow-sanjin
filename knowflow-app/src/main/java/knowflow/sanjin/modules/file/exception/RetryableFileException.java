package knowflow.sanjin.modules.file.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 文档解析可重试失败：未知故障、文件暂时不可读等。failureCode 用于区分失败原因（稳定错误码）。 */
public class RetryableFileException extends RuntimeException {

  private final String failureCode;

  public RetryableFileException(String message) {
    super(message);
    this.failureCode = ErrorCode.DOCUMENT_PARSE_FAILED;
  }

  public RetryableFileException(String message, Throwable cause) {
    super(message, cause);
    this.failureCode = ErrorCode.DOCUMENT_PARSE_FAILED;
  }

  public RetryableFileException(String failureCode, String message, Throwable cause) {
    super(message, cause);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
