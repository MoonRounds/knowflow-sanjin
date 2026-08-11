package knowflow.sanjin.modules.extraction.controller;

import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.service.ExtractionService;
import knowflow.sanjin.modules.extraction.vo.ExtractionTaskResponse;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.ProcessingTaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 会话知识提取触发入口。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class ExtractionController {

  private final ExtractionService service;
  private final ProcessingTaskService processingTaskService;
  private final CurrentOwnerProvider currentOwnerProvider;

  public ExtractionController(
      ExtractionService service,
      ProcessingTaskService processingTaskService,
      CurrentOwnerProvider currentOwnerProvider) {
    this.service = service;
    this.processingTaskService = processingTaskService;
    this.currentOwnerProvider = currentOwnerProvider;
  }

  @PostMapping("/conversations/{conversationId}/extraction")
  public ResponseEntity<ExtractionTaskResponse> trigger(@PathVariable String conversationId) {
    KnowledgeExtractionTask snapshot =
        service.trigger(ApiValueParser.positiveId(conversationId, "conversationId"));
    ProcessingTask task =
        processingTaskService.getByIdAndOwner(
            snapshot.getProcessingTaskId(), currentOwnerProvider.getCurrentOwnerId());
    return ResponseEntity.ok(ExtractionTaskAssembler.toResponse(snapshot, task.getStatus()));
  }
}
