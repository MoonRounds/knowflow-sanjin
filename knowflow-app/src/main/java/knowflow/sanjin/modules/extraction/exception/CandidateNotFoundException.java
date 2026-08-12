package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** Candidate 不存在或不属于当前 owner。 */
public class CandidateNotFoundException extends RuntimeException {

  private final Long id;

  public CandidateNotFoundException(Long id) {
    super("候选不存在或不可访问: id=" + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }

  public String getErrorCode() {
    return ErrorCode.CANDIDATE_NOT_FOUND;
  }
}
