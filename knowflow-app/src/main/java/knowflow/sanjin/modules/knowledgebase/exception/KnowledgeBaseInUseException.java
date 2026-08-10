package knowflow.sanjin.modules.knowledgebase.exception;

/** 删除 KnowledgeBase 会导致 KnowledgeItem 零归属（DECISIONS §10），阻止删除。 */
public class KnowledgeBaseInUseException extends RuntimeException {

  private final Long knowledgeBaseId;

  public KnowledgeBaseInUseException(Long knowledgeBaseId) {
    super(
        "KnowledgeBase is the only owner of some KnowledgeItems and cannot be deleted: "
            + knowledgeBaseId);
    this.knowledgeBaseId = knowledgeBaseId;
  }

  public Long getKnowledgeBaseId() {
    return knowledgeBaseId;
  }
}
