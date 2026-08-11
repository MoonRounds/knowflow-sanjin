package knowflow.sanjin.modules.document.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 文档解析可重试失败：未知故障、文件暂时不可读等。failureCode 用于区分失败原因（稳定错误码）。 */
public class RetryableDocumentException extends RuntimeException {

  private final String failureCode;

  public RetryableDocumentException(String message) {
    super(message);
    this.failureCode = ErrorCode.DOCUMENT_PARSE_FAILED;
  }

  public RetryableDocumentException(String message, Throwable cause) {
    super(message, cause);
    this.failureCode = ErrorCode.DOCUMENT_PARSE_FAILED;
  }

  public RetryableDocumentException(String failureCode, String message, Throwable cause) {
    super(message, cause);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
