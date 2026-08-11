package knowflow.sanjin.modules.document.exception;

/** 按 id 查询 FileMetadata 时不存在或不属于当前 Owner。 */
public class FileMetadataNotFoundException extends RuntimeException {
  private final Long id;

  public FileMetadataNotFoundException(Long id) {
    super("FileMetadata with id=" + id + " does not exist or is not accessible.");
    this.id = id;
  }

  public Long getId() {
    return id;
  }
}
