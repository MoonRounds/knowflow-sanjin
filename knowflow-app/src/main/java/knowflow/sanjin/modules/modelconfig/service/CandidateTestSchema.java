package knowflow.sanjin.modules.modelconfig.service;

import java.util.List;

/**
 * Candidate Extraction Structured Output 测试 Schema。仅用于 Utility Model 能力校验， 不承载 Extraction 业务（Phase
 * 7 才会引入真实 Candidate）。
 */
public final class CandidateTestSchema {

  private List<Candidate> candidates;

  public List<Candidate> getCandidates() {
    return candidates;
  }

  public void setCandidates(List<Candidate> candidates) {
    this.candidates = candidates;
  }

  public static class Candidate {
    private String title;
    private String knowledgeBaseId;
    private String content;

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getKnowledgeBaseId() {
      return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
      this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }
  }
}
