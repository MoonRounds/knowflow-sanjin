package knowflow.sanjin.modules.extraction.dto;

import java.util.List;

/**
 * 提取 Structured Output Schema（与 ExtractionConstants.EXTRACTION_PROFILE_VERSION=1 绑定）。
 *
 * <p>字段与提取 Prompt 输出约定一致：{@code knowledgeBaseId} 单归属，必须来自给定目录；{@code tags} 可空。 Bean 需有默认构造与 setter
 * 供 BeanOutputConverter 反序列化。
 */
public class ExtractionResult {

  private List<Candidate> candidates;

  public List<Candidate> getCandidates() {
    return candidates;
  }

  public void setCandidates(List<Candidate> candidates) {
    this.candidates = candidates;
  }

  public static class Candidate {
    private String title;
    private String summary;
    private String content;
    private String knowledgeBaseId;
    private List<String> tags;
    private String reason;

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

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
