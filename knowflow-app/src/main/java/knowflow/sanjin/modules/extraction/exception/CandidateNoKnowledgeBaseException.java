package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** Candidate 草稿未关联任何 KnowledgeBase，无法创建 Item（Item 必须归属 1～N 个 KB）。 */
public class CandidateNoKnowledgeBaseException extends RuntimeException {

  public CandidateNoKnowledgeBaseException(Long id) {
    super("候选 " + id + " 草稿必须关联至少一个知识库");
  }

  public String getErrorCode() {
    return ErrorCode.CANDIDATE_NO_KNOWLEDGE_BASE;
  }
}
