package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 提取任务快照不存在或不属于当前 owner。 */
public class ExtractionTaskNotFoundException extends RuntimeException {

  private final Long id;

  public ExtractionTaskNotFoundException(Long id) {
    super("Extraction task snapshot with id=" + id + " does not exist or is not accessible");
    this.id = id;
  }

  public Long getId() {
    return id;
  }

  public String getErrorCode() {
    return ErrorCode.EXTRACTION_TASK_NOT_FOUND;
  }
}
