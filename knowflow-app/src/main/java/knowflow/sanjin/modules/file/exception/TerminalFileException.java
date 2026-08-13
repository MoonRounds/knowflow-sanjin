package knowflow.sanjin.modules.file.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 文档解析终态失败：文件损坏、不可恢复的解析错误。failureCode 用于区分失败原因（稳定错误码）。 */
public class TerminalFileException extends RuntimeException {

  private final String failureCode;

  public TerminalFileException(String message) {
    super(message);
    this.failureCode = ErrorCode.DOCUMENT_PARSE_FAILED;
  }

  public TerminalFileException(String failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
