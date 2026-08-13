package knowflow.sanjin.modules.file.exception;

/** 文档解析失败（异步任务错误，非 HTTP 直接响应）。 */
public class FileParseException extends RuntimeException {
  public FileParseException(String message) {
    super(message);
  }
}
