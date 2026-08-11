package knowflow.sanjin.modules.extraction.exception;

/** 不可重试提取异常：结构化输出修复一次后仍非法、Utility 能力不可用等终态错误。Consumer 直接标记任务 FAILED 并进入 DLQ。 */
public class TerminalExtractionException extends RuntimeException {

  private final String failureCode;

  public TerminalExtractionException(String failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
