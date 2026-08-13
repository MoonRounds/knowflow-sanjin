package knowflow.sanjin.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 更新 Manual Note 请求：字段未传保持不变；正文编辑会递增 contentVersion 并触发新版本索引； knowledgeBaseId 必填，强制保留单归属。rowVersion
 * 用于乐观锁条件更新。
 */
public class UpdateDocumentRequest {

  private String title;

  @Size(max = 2000, message = "{knowledge.summary.max}")
  private String summary;

  @Size(max = 200000, message = "{knowledge.content.max}")
  private String content;

  @NotBlank(message = "{knowledge.knowledgeBaseId.required}")
  @Size(max = 200, message = "{knowledge.knowledgeBaseId.max}")
  private String knowledgeBaseId;

  private List<String> tags;

  @NotNull(message = "{knowledge.rowVersion.required}")
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

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public void setKnowledgeBaseId(String knowledgeBaseId) {
    this.knowledgeBaseId = knowledgeBaseId;
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
