package knowflow.sanjin.modules.extraction.controller;

import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.vo.ExtractionTaskResponse;
import knowflow.sanjin.modules.processing.ProcessingConstants;

/** 提取任务快照 → 响应映射；状态来自关联 ProcessingTask，快照表不重复保存状态。 */
public final class ExtractionTaskAssembler {

  private ExtractionTaskAssembler() {}

  public static ExtractionTaskResponse toResponse(
      KnowledgeExtractionTask task, String processingStatus) {
    ExtractionTaskResponse r = new ExtractionTaskResponse();
    r.setId(String.valueOf(task.getId()));
    r.setConversationId(String.valueOf(task.getConversationId()));
    r.setCutoffMessageId(String.valueOf(task.getCutoffMessageId()));
    r.setExtractionProfile(task.getExtractionProfile());
    r.setProfileVersion(task.getProfileVersion());
    r.setUtilityRevisionId(String.valueOf(task.getUtilityRevisionId()));
    r.setProcessingTaskId(String.valueOf(task.getProcessingTaskId()));
    r.setInputCharCount(task.getInputCharCount());
    r.setCandidateCount(task.getCandidateCount());
    r.setStatus(processingStatus != null ? processingStatus : ProcessingConstants.STATUS_PENDING);
    return r;
  }

  /** 不查询 ProcessingTask 状态时使用（例如快照刚创建、状态必然 PENDING）。 */
  public static ExtractionTaskResponse toResponse(KnowledgeExtractionTask task) {
    return toResponse(task, ProcessingConstants.STATUS_PENDING);
  }
}
