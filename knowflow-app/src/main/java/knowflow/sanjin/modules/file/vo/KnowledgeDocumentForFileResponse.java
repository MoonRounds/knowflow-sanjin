package knowflow.sanjin.modules.file.vo;

/** 上传后关联的 KnowledgeDocument 摘要视图（供前端跳转详情）。 */
public class KnowledgeDocumentForFileResponse {

  private String id;
  private String title;
  private String sourceType;
  private String indexStatus;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public String getIndexStatus() {
    return indexStatus;
  }

  public void setIndexStatus(String indexStatus) {
    this.indexStatus = indexStatus;
  }
}
