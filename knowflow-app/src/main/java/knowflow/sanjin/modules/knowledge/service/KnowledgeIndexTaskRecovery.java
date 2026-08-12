package knowflow.sanjin.modules.knowledge.service;

import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.ProcessingTaskDomainRecovery;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndexTaskRecovery implements ProcessingTaskDomainRecovery {

  private final KnowledgeIndexStateService indexStateService;

  public KnowledgeIndexTaskRecovery(KnowledgeIndexStateService indexStateService) {
    this.indexStateService = indexStateService;
  }

  @Override
  public boolean supports(ProcessingTask task) {
    return ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX.equals(task.getTaskType());
  }

  @Override
  public void markProcessing(ProcessingTask task) {
    indexStateService.markProcessing(task);
  }

  @Override
  public void markFailed(ProcessingTask task, String failureCode, String lastError) {
    indexStateService.markFailed(task, failureCode, lastError);
  }

  @Override
  public void prepareForRepublish(ProcessingTask task) {
    indexStateService.markPending(task);
  }
}
