package knowflow.sanjin.modules.extraction.exception;

import knowflow.sanjin.common.error.ErrorCode;

/** 提取任务快照不存在或不属于当前 owner。 */
public class ExtractionTaskNotFoundException extends RuntimeException {

  private final Long id;

  public ExtractionTaskNotFoundException(Long id) {
    super("提取任务快照不存在或不可访问: id=" + id);
    this.id = id;
  }

  public Long getId() {
    return id;
  }

  public String getErrorCode() {
    return ErrorCode.EXTRACTION_TASK_NOT_FOUND;
  }
}
