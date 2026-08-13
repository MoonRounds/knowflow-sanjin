package knowflow.sanjin.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 创建 Manual Note 请求：必须关联一个 KnowledgeBase（单归属）才可进入索引生命周期；tags 可选。 title 缺省取正文首行（安全截断）。 */
public class CreateDocumentRequest {

  private String title;

  @Size(max = 2000, message = "{knowledge.summary.max}")
  private String summary;

  @NotBlank(message = "{knowledge.content.required}")
  @Size(max = 200000, message = "{knowledge.content.max}")
  private String content;

  @NotBlank(message = "{knowledge.knowledgeBaseId.required}")
  @Size(max = 200, message = "{knowledge.knowledgeBaseId.max}")
  private String knowledgeBaseId;

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
}
