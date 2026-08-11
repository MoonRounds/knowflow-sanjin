package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** Candidate 处于不允许的状态：已确认的候选不可编辑、不可再确认；并发确认冲突等。 */
public class CandidateInvalidStateException extends RuntimeException {

  public CandidateInvalidStateException(String message) {
    super(message);
  }

  public String getErrorCode() {
    return ErrorCode.CANDIDATE_INVALID_STATE;
  }
}
