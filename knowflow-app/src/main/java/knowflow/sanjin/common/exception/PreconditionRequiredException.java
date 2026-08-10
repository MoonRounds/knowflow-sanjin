package knowflow.sanjin.common.exception;

/** 请求缺少前置条件（如 If-Match 头），映射为 428 Precondition Required。 */
public class PreconditionRequiredException extends RuntimeException {

  public PreconditionRequiredException(String message) {
    super(message);
  }
}
