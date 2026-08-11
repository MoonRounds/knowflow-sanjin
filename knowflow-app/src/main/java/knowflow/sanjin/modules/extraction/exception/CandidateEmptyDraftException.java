package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** Candidate 草稿缺少必填字段（title/content），确认被拒绝。 */
public class CandidateEmptyDraftException extends RuntimeException {

  public CandidateEmptyDraftException(Long id, String field) {
    super("Candidate " + id + " draft is missing required field: " + field);
  }

  public String getErrorCode() {
    return ErrorCode.CANDIDATE_EMPTY_DRAFT;
  }
}
