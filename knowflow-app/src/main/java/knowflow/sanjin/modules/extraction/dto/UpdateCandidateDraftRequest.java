package knowflow.sanjin.modules.extraction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Candidate 草稿编辑请求：完整覆盖草稿字段；随 If-Match/ETag 传递 rowVersion 乐观锁。 */
public class UpdateCandidateDraftRequest {

  @NotBlank private String title;

  private String summary;

  @NotBlank private String content;

  @NotEmpty private List<String> knowledgeBaseIds;

  private List<String> tags;

  private Integer rowVersion;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public List<String> getKnowledgeBaseIds() {
    return knowledgeBaseIds;
  }

  public void setKnowledgeBaseIds(List<String> knowledgeBaseIds) {
    this.knowledgeBaseIds = knowledgeBaseIds;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public Integer getRowVersion() {
    return rowVersion;
  }

  public void setRowVersion(Integer rowVersion) {
    this.rowVersion = rowVersion;
  }
}
