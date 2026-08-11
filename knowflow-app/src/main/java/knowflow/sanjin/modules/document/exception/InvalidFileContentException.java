package knowflow.sanjin.modules.document.exception;

/** 文件内容校验失败（非文本 / 非法 UTF-8 / 空文件 / 超限）。 */
public class InvalidFileContentException extends RuntimeException {
  public InvalidFileContentException(String message) {
    super(message);
  }
}
