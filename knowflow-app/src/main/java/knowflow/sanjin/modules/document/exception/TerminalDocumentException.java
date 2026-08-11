package knowflow.sanjin.modules.document.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 文档解析终态失败：文件损坏、不可恢复的解析错误。failureCode 用于区分失败原因（稳定错误码）。 */
public class TerminalDocumentException extends RuntimeException {

  private final String failureCode;

  public TerminalDocumentException(String message) {
    super(message);
    this.failureCode = ErrorCode.DOCUMENT_PARSE_FAILED;
  }

  public TerminalDocumentException(String failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
