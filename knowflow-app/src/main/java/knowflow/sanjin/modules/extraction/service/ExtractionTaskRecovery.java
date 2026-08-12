package knowflow.sanjin.modules.extraction.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeExtractionTaskMapper;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.ProcessingTaskDomainRecovery;
import org.springframework.stereotype.Component;

@Component
public class ExtractionTaskRecovery implements ProcessingTaskDomainRecovery {

  private final KnowledgeExtractionTaskMapper extractionTaskMapper;

  public ExtractionTaskRecovery(KnowledgeExtractionTaskMapper extractionTaskMapper) {
    this.extractionTaskMapper = extractionTaskMapper;
  }

  @Override
  public boolean supports(ProcessingTask task) {
    return ExtractionConstants.TASK_TYPE_EXTRACTION.equals(task.getTaskType());
  }

  @Override
  public String queueBase() {
    return ExtractionConstants.WORK_QUEUE_BASE;
  }

  @Override
  public void prepareForRepublish(ProcessingTask task) {
    // Snapshot 状态随 ProcessingTask 状态读取；租约恢复无需重绑 taskId。
  }

  @Override
  public void prepareForRetry(ProcessingTask original, ProcessingTask retry) {
    extractionTaskMapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeExtractionTask>()
            .eq(KnowledgeExtractionTask::getProcessingTaskId, original.getId())
            .eq(KnowledgeExtractionTask::getOwnerId, original.getOwnerId())
            .set(KnowledgeExtractionTask::getProcessingTaskId, retry.getId())
            .set(KnowledgeExtractionTask::getCandidateCount, null));
  }
}
