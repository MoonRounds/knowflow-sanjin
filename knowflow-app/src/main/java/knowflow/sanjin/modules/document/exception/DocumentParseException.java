package knowflow.sanjin.modules.document.exception;

/** 文档解析失败（异步任务错误，非 HTTP 直接响应）。 */
public class DocumentParseException extends RuntimeException {
  public DocumentParseException(String message) {
    super(message);
  }
}
