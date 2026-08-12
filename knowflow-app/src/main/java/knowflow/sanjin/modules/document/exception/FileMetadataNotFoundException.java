package knowflow.sanjin.modules.document.exception;

/** 按 id 查询 FileMetadata 时不存在或不属于当前 Owner。 */
public class FileMetadataNotFoundException extends RuntimeException {
  private final Long id;

  public FileMetadataNotFoundException(Long id) {
    super("文件元数据不存在或不可访问: id=" + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
