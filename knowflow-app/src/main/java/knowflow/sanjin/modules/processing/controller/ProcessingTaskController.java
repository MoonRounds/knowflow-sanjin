package knowflow.sanjin.modules.processing.controller;

import java.util.List;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.assembler.ProcessingTaskAssembler;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.ProcessingTaskService;
import knowflow.sanjin.modules.processing.vo.ProcessingTaskResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Processing 任务 REST 入口：轻量列表查询与失败任务手动 Retry。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class ProcessingTaskController {

  private final ProcessingTaskService service;
  private final CurrentOwnerProvider currentOwnerProvider;

  public ProcessingTaskController(
      ProcessingTaskService service, CurrentOwnerProvider currentOwnerProvider) {
    this.service = service;
    this.currentOwnerProvider = currentOwnerProvider;
  }

  @GetMapping("/processing-tasks")
  public List<ProcessingTaskResponse> list(
      @RequestParam(name = "status", required = false) String status) {
    return service.listForOwner(status).stream().map(ProcessingTaskAssembler::toResponse).toList();
  }

  @PostMapping("/processing-tasks/{id}/retry")
  public ResponseEntity<ProcessingTaskResponse> retry(@PathVariable String id) {
    ProcessingTask retry = service.manualRetry(ApiValueParser.positiveId(id, "id"));
    return ResponseEntity.ok(ProcessingTaskAssembler.toResponse(retry));
  }
}
