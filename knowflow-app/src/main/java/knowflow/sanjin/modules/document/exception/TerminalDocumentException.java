package knowflow.sanjin.modules.document.exception;

/** 文档解析终态失败：文件损坏、不可恢复的解析错误。 */
public class TerminalDocumentException extends RuntimeException {
  public TerminalDocumentException(String message) {
    super(message);
  }
}
