package knowflow.sanjin.modules.rag.dto;

import java.util.List;

/** 检索阶段诊断信息（供 GenerationTrace 持久化，不含完整正文）。 */
public class RetrievalTrace {

  private String retrievalQuery;

  private List<Long> selectedKnowledgeBaseIds;

  private int qdrantCandidates;

  private int injectedCount;

  private int discardedByValidation;

  private String failure;

  public String getRetrievalQuery() {
    return retrievalQuery;
  }

  public void setRetrievalQuery(String retrievalQuery) {
    this.retrievalQuery = retrievalQuery;
  }

  public List<Long> getSelectedKnowledgeBaseIds() {
    return selectedKnowledgeBaseIds;
  }

  public void setSelectedKnowledgeBaseIds(List<Long> selectedKnowledgeBaseIds) {
    this.selectedKnowledgeBaseIds = selectedKnowledgeBaseIds;
  }

  public int getQdrantCandidates() {
    return qdrantCandidates;
  }

  public void setQdrantCandidates(int qdrantCandidates) {
    this.qdrantCandidates = qdrantCandidates;
  }

  public int getInjectedCount() {
    return injectedCount;
  }

  public void setInjectedCount(int injectedCount) {
    this.injectedCount = injectedCount;
  }

  public int getDiscardedByValidation() {
    return discardedByValidation;
  }

  public void setDiscardedByValidation(int discardedByValidation) {
    this.discardedByValidation = discardedByValidation;
  }

  public String getFailure() {
    return failure;
  }

  public void setFailure(String failure) {
    this.failure = failure;
  }
}
