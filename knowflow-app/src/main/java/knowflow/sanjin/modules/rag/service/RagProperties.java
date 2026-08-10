package knowflow.sanjin.modules.rag.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 6 RAG 配置：路由、检索与注入上限；阈值与 Top-K 由 Eval 校准。
 *
 * <p>各外部调用（Router / Embedding / Qdrant）的传输超时由既有客户端配置兜底（ModelClientProperties、EmbeddingProperties、
 * QdrantProperties），此处不再重复声明超时项。
 */
@ConfigurationProperties(prefix = "knowflow.rag")
public class RagProperties {

  /** 可路由 KnowledgeBase 目录最大条数；超过时按名称排序截断并在提示中注明。 */
  private int catalogLimit = 50;

  /** 全局检索 Top-K（跨选中知识库一次检索）。 */
  private int topK = 8;

  /** 相似度阈值；低于该分数的候选不注入 Prompt。 */
  private double scoreThreshold = 0.2;

  /** 注入 Prompt 的 RAG 材料总字符预算（确定性裁剪）。 */
  private int contextCharBudget = 3_000;

  /** 单个 Router 输入提示中的上下文上限（超过截断，避免超大目录/历史撑爆 prompt）。 */
  private int routerContextCharLimit = 1_500;

  /** 单个来源片段注入上限（字符）。 */
  private int snippetCharLimit = 200;

  public int getCatalogLimit() {
    return catalogLimit;
  }

  public void setCatalogLimit(int catalogLimit) {
    this.catalogLimit = catalogLimit;
  }

  public int getTopK() {
    return topK;
  }

  public void setTopK(int topK) {
    this.topK = topK;
  }

  public double getScoreThreshold() {
    return scoreThreshold;
  }

  public void setScoreThreshold(double scoreThreshold) {
    this.scoreThreshold = scoreThreshold;
  }

  public int getContextCharBudget() {
    return contextCharBudget;
  }

  public void setContextCharBudget(int contextCharBudget) {
    this.contextCharBudget = contextCharBudget;
  }

  public int getRouterContextCharLimit() {
    return routerContextCharLimit;
  }

  public void setRouterContextCharLimit(int routerContextCharLimit) {
    this.routerContextCharLimit = routerContextCharLimit;
  }

  public int getSnippetCharLimit() {
    return snippetCharLimit;
  }

  public void setSnippetCharLimit(int snippetCharLimit) {
    this.snippetCharLimit = snippetCharLimit;
  }
}
