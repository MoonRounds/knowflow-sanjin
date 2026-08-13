package knowflow.sanjin.modules.file.exception;

/** 文件扩展名不在支持列表（V1 仅 md / markdown / txt）。 */
public class FileUnsupportedTypeException extends RuntimeException {
  public FileUnsupportedTypeException(String message) {
    super(message);
  }
}
