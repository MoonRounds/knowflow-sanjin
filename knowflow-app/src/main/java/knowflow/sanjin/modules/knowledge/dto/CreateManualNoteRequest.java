package knowflow.sanjin.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 创建 Manual Note 请求：至少关联一个 KnowledgeBase 才可进入索引生命周期；tags 可选。 title 缺省取正文首行（安全截断）。 */
public class CreateManualNoteRequest {

  private String title;

  @Size(max = 2000, message = "{knowledge.summary.max}")
  private String summary;

  @NotBlank(message = "{knowledge.content.required}")
  @Size(max = 200000, message = "{knowledge.content.max}")
  private String content;

  @NotEmpty(message = "{knowledge.knowledgeBaseIds.required}")
  private List<@Size(max = 200, message = "{knowledge.knowledgeBaseId.max}") String>
      knowledgeBaseIds;

  private List<String> tags;

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
}
