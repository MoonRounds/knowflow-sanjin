package knowflow.sanjin.modules.rag.exception;

/** Router 调用失败（解析失败修复后仍失败 / Provider 异常 / 超时），可降级为普通回答。 */
public class RouterException extends RuntimeException {

  public RouterException(String message) {
    super(message);
  }

  public RouterException(String message, Throwable cause) {
    super(message, cause);
  }
}
