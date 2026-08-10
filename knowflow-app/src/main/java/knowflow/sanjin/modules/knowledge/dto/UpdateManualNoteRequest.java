package knowflow.sanjin.modules.knowledge.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 更新 Manual Note 请求：字段未传保持不变；正文编辑会递增 contentVersion 并触发新版本索引； knowledgeBaseIds
 * 至少一个，强制保留归属。rowVersion 用于乐观锁条件更新。
 */
public class UpdateManualNoteRequest {

  private String title;

  @Size(max = 2000, message = "{knowledge.summary.max}")
  private String summary;

  @Size(max = 200000, message = "{knowledge.content.max}")
  private String content;

  @NotEmpty(message = "{knowledge.knowledgeBaseIds.required}")
  private List<@Size(max = 200, message = "{knowledge.knowledgeBaseId.max}") String>
      knowledgeBaseIds;

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
