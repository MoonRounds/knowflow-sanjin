package knowflow.sanjin.modules.document.vo;

/** 上传返回：重复命中时携带已有文件/Item 与 {@code duplicate=true}。 */
public class FileUploadResponse {

  private FileMetadataResponse file;
  private KnowledgeItemForFileResponse item;
  private boolean duplicate;

  public FileMetadataResponse getFile() {
    return file;
  }

  public void setFile(FileMetadataResponse file) {
    this.file = file;
  }

  public KnowledgeItemForFileResponse getItem() {
    return item;
  }

  public void setItem(KnowledgeItemForFileResponse item) {
    this.item = item;
  }

  public boolean isDuplicate() {
    return duplicate;
  }

  public void setDuplicate(boolean duplicate) {
    this.duplicate = duplicate;
  }
}
