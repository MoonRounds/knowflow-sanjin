package knowflow.sanjin.modules.document.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import knowflow.sanjin.modules.document.DocumentConstants;
import knowflow.sanjin.modules.document.entity.FileMetadata;
import knowflow.sanjin.modules.document.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.ProcessingTaskDomainRecovery;
import org.springframework.stereotype.Component;

@Component
public class DocumentParseTaskRecovery implements ProcessingTaskDomainRecovery {

  private final FileMetadataMapper fileMapper;

  public DocumentParseTaskRecovery(FileMetadataMapper fileMapper) {
    this.fileMapper = fileMapper;
  }

  @Override
  public boolean supports(ProcessingTask task) {
    return DocumentConstants.TASK_TYPE_DOCUMENT_PARSE.equals(task.getTaskType());
  }

  @Override
  public String queueBase() {
    return DocumentConstants.WORK_QUEUE_BASE;
  }

  @Override
  public void markProcessing(ProcessingTask task) {
    update(task, DocumentConstants.PARSE_STATUS_PROCESSING, null, null);
  }

  @Override
  public void markFailed(ProcessingTask task, String failureCode, String lastError) {
    update(task, DocumentConstants.PARSE_STATUS_FAILED, failureCode, lastError);
  }

  @Override
  public void prepareForRepublish(ProcessingTask task) {
    update(task, DocumentConstants.PARSE_STATUS_PENDING, null, null);
  }

  private void update(
      ProcessingTask task, String parseStatus, String failureCode, String lastError) {
    if (task.getBusinessId() == null) {
      return;
    }
    fileMapper.update(
        null,
        new LambdaUpdateWrapper<FileMetadata>()
            .eq(FileMetadata::getId, task.getBusinessId())
            .eq(FileMetadata::getOwnerId, task.getOwnerId())
            .set(FileMetadata::getParseStatus, parseStatus)
            .set(FileMetadata::getParseErrorCode, failureCode)
            .set(FileMetadata::getParseErrorMessage, lastError));
  }
}
