package knowflow.sanjin.modules.rag.dto;

import java.util.List;

/**
 * Router Structured Output 输出（真实业务契约，DECISIONS §13）。
 *
 * <p>{@code needRag} 必填；{@code knowledgeBaseIds} 0～3 个；{@code retrievalQuery} 可选（空则回退当前问题原文）；
 * {@code routeScores} 仅用于 Trace/Eval 诊断，不参与阈值判定。
 */
public class RouterResult {

  private boolean needRag;

  private List<String> knowledgeBaseIds;

  private String retrievalQuery;

  private List<RouteScore> routeScores;

  public boolean isNeedRag() {
    return needRag;
  }

  public void setNeedRag(boolean needRag) {
    this.needRag = needRag;
  }

  public List<String> getKnowledgeBaseIds() {
    return knowledgeBaseIds;
  }

  public void setKnowledgeBaseIds(List<String> knowledgeBaseIds) {
    this.knowledgeBaseIds = knowledgeBaseIds;
  }

  public String getRetrievalQuery() {
    return retrievalQuery;
  }

  public void setRetrievalQuery(String retrievalQuery) {
    this.retrievalQuery = retrievalQuery;
  }

  public List<RouteScore> getRouteScores() {
    return routeScores;
  }

  public void setRouteScores(List<RouteScore> routeScores) {
    this.routeScores = routeScores;
  }

  /** 诊断用路由评分（不进阈值，不参与合法性校验）。 */
  public static class RouteScore {
    private String knowledgeBaseId;
    private double score;

    public String getKnowledgeBaseId() {
      return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
      this.knowledgeBaseId = knowledgeBaseId;
    }

    public double getScore() {
      return score;
    }

    public void setScore(double score) {
      this.score = score;
    }
  }
}
