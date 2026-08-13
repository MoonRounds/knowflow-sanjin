package knowflow.sanjin.modules.file.exception;

/** 文件下载时正式原文件缺失（数据库有记录但磁盘文件丢失）。 */
public class StoredFileMissingException extends RuntimeException {
  public StoredFileMissingException(String message) {
    super(message);
  }
}
