package knowflow.sanjin.modules.document.exception;

/** 文档解析可重试失败：未知故障、文件暂时不可读等。 */
public class RetryableDocumentException extends RuntimeException {
  public RetryableDocumentException(String message) {
    super(message);
  }

  public RetryableDocumentException(String message, Throwable cause) {
    super(message, cause);
  }
}
