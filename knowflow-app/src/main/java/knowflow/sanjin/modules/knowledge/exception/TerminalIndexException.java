package knowflow.sanjin.modules.knowledge.exception;

/** 不可重试索引异常：认证/鉴权失败、维度不匹配、Payload schema 校验失败、内容为空等终态错误。 Consumer 直接标记任务 FAILED 并进入 DLQ。 */
public class TerminalIndexException extends RuntimeException {

  private final String failureCode;

  public TerminalIndexException(String failureCode, String message) {
    super(message);
    this.failureCode = failureCode;
  }

  public String getFailureCode() {
    return failureCode;
  }
}
