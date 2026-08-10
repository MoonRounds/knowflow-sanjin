package knowflow.sanjin.modules.processing.assembler;

import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.vo.ProcessingTaskResponse;

/** Entity 与 API 模型的显式转换；不暴露 ownerId 等内部字段。 */
public final class ProcessingTaskAssembler {

  private ProcessingTaskAssembler() {}

  public static ProcessingTaskResponse toResponse(ProcessingTask task) {
    ProcessingTaskResponse r = new ProcessingTaskResponse();
    r.setId(task.getId().toString());
    r.setTaskType(task.getTaskType());
    r.setBusinessKey(task.getBusinessKey());
    r.setBusinessId(task.getBusinessId() != null ? task.getBusinessId().toString() : null);
    r.setStatus(task.getStatus());
    r.setRetryCount(task.getRetryCount() != null ? task.getRetryCount() : 0);
    r.setMaxRetries(task.getMaxRetries() != null ? task.getMaxRetries() : 0);
    r.setFailureCode(task.getFailureCode());
    r.setLastError(task.getLastError());
    r.setRetryOfTaskId(task.getRetryOfTaskId() != null ? task.getRetryOfTaskId().toString() : null);
    r.setCreatedAt(task.getCreatedAt());
    r.setUpdatedAt(task.getUpdatedAt());
    return r;
  }
}
