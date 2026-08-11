package knowflow.sanjin.modules.document.exception;

/** 上传文件超过配置大小上限（默认 5 MiB）。 */
public class FileTooLargeException extends RuntimeException {
  public FileTooLargeException(String message) {
    super(message);
  }
}
