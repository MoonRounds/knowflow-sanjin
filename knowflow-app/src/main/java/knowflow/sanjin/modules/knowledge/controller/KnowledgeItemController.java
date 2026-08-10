package knowflow.sanjin.modules.knowledge.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.knowledge.assembler.KnowledgeItemAssembler;
import knowflow.sanjin.modules.knowledge.dto.CreateManualNoteRequest;
import knowflow.sanjin.modules.knowledge.dto.UpdateManualNoteRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.service.KnowledgeService;
import knowflow.sanjin.modules.knowledge.vo.KnowledgeItemResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Manual Note REST 入口：BIGINT id 走字符串路径参数，写操作通过 If-Match/ETag 传递乐观锁版本。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class KnowledgeItemController {

  private final KnowledgeService service;

  public KnowledgeItemController(KnowledgeService service) {
    this.service = service;
  }

  @PostMapping("/knowledge-items")
  public ResponseEntity<KnowledgeItemResponse> createManualNote(
      @Valid @RequestBody CreateManualNoteRequest request) {
    KnowledgeItem item = service.createManualNote(request);
    KnowledgeItemResponse response = toResponse(item);
    return ResponseEntity.created(URI.create("/api/v1/knowledge-items/" + response.getId()))
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @GetMapping("/knowledge-items")
  public List<KnowledgeItemResponse> list() {
    List<KnowledgeItem> items = service.listForOwner();
    List<Long> itemIds = items.stream().map(KnowledgeItem::getId).toList();
    Map<Long, List<Long>> kbIds = service.batchKnowledgeBaseIds(itemIds);
    Map<Long, List<String>> tags = service.batchTagNames(itemIds);
    return KnowledgeItemAssembler.toResponseList(items, kbIds, tags);
  }

  @GetMapping("/knowledge-items/{id}")
  public ResponseEntity<KnowledgeItemResponse> get(@PathVariable String id) {
    KnowledgeItem item = service.getByIdAndOwner(ApiValueParser.positiveId(id, "id"));
    KnowledgeItemResponse response = toResponse(item);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @PutMapping("/knowledge-items/{id}")
  public ResponseEntity<KnowledgeItemResponse> update(
      @PathVariable String id, @Valid @RequestBody UpdateManualNoteRequest request) {
    KnowledgeItem item = service.updateManualNote(ApiValueParser.positiveId(id, "id"), request);
    KnowledgeItemResponse response = toResponse(item);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @DeleteMapping("/knowledge-items/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    service.softDelete(
        ApiValueParser.positiveId(id, "id"), ApiValueParser.requiredStrongEtagVersion(ifMatch));
    return ResponseEntity.noContent().build();
  }

  private KnowledgeItemResponse toResponse(KnowledgeItem item) {
    return KnowledgeItemAssembler.toResponse(
        item, service.getKnowledgeBaseIds(item.getId()), service.getTagNames(item.getId()));
  }
}
