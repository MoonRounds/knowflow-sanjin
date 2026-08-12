package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** Candidate 乐观锁冲突：提交的 rowVersion 与当前版本不一致。 */
public class CandidateVersionConflictException extends RuntimeException {

  public CandidateVersionConflictException(Long id) {
    super("候选 " + id + " 已被并发修改，请刷新后重试");
  }

  public String getErrorCode() {
    return ErrorCode.CANDIDATE_VERSION_CONFLICT;
  }
}
