package knowflow.sanjin.modules.knowledgebase.exception;

/** 删除 KnowledgeBase 会导致 KnowledgeDocument 零归属（ADR 0007 单归属），阻止删除。 */
public class KnowledgeBaseInUseException extends RuntimeException {

  private final Long knowledgeBaseId;

  public KnowledgeBaseInUseException(Long knowledgeBaseId) {
    super("知识库是部分知识条目的唯一归属，无法删除: " + knowledgeBaseId);
    this.knowledgeBaseId = knowledgeBaseId;
  }

  public Long getKnowledgeBaseId() {
    return knowledgeBaseId;
  }
}
