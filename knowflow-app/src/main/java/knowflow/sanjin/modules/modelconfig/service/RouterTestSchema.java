package knowflow.sanjin.modules.modelconfig.service;

import java.util.List;

/**
 * Router Structured Output 测试 Schema。仅用于 Utility Model 能力校验， 不承载 Router 业务（Phase 6 才会引入真实 Router）。
 */
public final class RouterTestSchema {

  private boolean needRag;
  private List<KnowledgeBaseRef> knowledgeBases;
  private String retrievalQuery;

  public boolean isNeedRag() {
    return needRag;
  }

  public void setNeedRag(boolean needRag) {
    this.needRag = needRag;
  }

  public List<KnowledgeBaseRef> getKnowledgeBases() {
    return knowledgeBases;
  }

  public void setKnowledgeBases(List<KnowledgeBaseRef> knowledgeBases) {
    this.knowledgeBases = knowledgeBases;
  }

  public String getRetrievalQuery() {
    return retrievalQuery;
  }

  public void setRetrievalQuery(String retrievalQuery) {
    this.retrievalQuery = retrievalQuery;
  }

  public static class KnowledgeBaseRef {
    private String id;
    private String name;
    private double score;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }
  }
}
