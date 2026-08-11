package knowflow.sanjin.modules.extraction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Phase 7 提取配置：输入预算、单候选推荐上限与任务重试。 */
@ConfigurationProperties(prefix = "knowflow.extraction")
public class ExtractionProperties {

  /** 输入预算（字符）：超长直接拒绝且不调用 LLM，不静默截断（DECISIONS §11）。 */
  private int inputCharBudget = 20_000;

  /** 一次提取返回的候选上限（V1 默认 10）。 */
  private int maxCandidates = 10;

  /** 单个候选最多推荐 KnowledgeBase 数（与 Router 对齐）。 */
  private int maxKnowledgeBasesPerCandidate = 3;

  /** 单个候选最多推荐 tag 数。 */
  private int maxTagsPerCandidate = 5;

  /** 提取任务最大重试次数（写入 ProcessingTask.maxRetries）。 */
  private int maxRetries = 3;

  public int getInputCharBudget() {
    return inputCharBudget;
  }

  public void setInputCharBudget(int inputCharBudget) {
    this.inputCharBudget = inputCharBudget;
  }

  public int getMaxCandidates() {
    return maxCandidates;
  }

  public void setMaxCandidates(int maxCandidates) {
    this.maxCandidates = maxCandidates;
  }

  public int getMaxKnowledgeBasesPerCandidate() {
    return maxKnowledgeBasesPerCandidate;
  }

  public void setMaxKnowledgeBasesPerCandidate(int maxKnowledgeBasesPerCandidate) {
    this.maxKnowledgeBasesPerCandidate = maxKnowledgeBasesPerCandidate;
  }

  public int getMaxTagsPerCandidate() {
    return maxTagsPerCandidate;
  }

  public void setMaxTagsPerCandidate(int maxTagsPerCandidate) {
    this.maxTagsPerCandidate = maxTagsPerCandidate;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }
}
