package knowflow.sanjin.modules.knowledgebase.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.knowledgebase.assembler.KnowledgeBaseAssembler;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.knowledgebase.vo.KnowledgeBaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** KnowledgeBase REST 入口：BIGINT id 走字符串路径参数，写操作通过 If-Match/ETag 传递乐观锁版本。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class KnowledgeBaseController {

  private final KnowledgeBaseService service;

  public KnowledgeBaseController(KnowledgeBaseService service) {
    this.service = service;
  }

  @PostMapping("/knowledge-bases")
  public ResponseEntity<KnowledgeBaseResponse> create(
      @Valid @RequestBody CreateKnowledgeBaseRequest request) {
    KnowledgeBase kb = service.create(request);
    KnowledgeBaseResponse response = KnowledgeBaseAssembler.toResponse(kb);
    return ResponseEntity.created(URI.create("/api/v1/knowledge-bases/" + response.getId()))
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @GetMapping("/knowledge-bases")
  public List<KnowledgeBaseResponse> list() {
    return KnowledgeBaseAssembler.toResponseList(service.listForOwner());
  }

  @GetMapping("/knowledge-bases/{id}")
  public ResponseEntity<KnowledgeBaseResponse> get(@PathVariable String id) {
    KnowledgeBase kb = service.getByIdAndOwner(ApiValueParser.positiveId(id, "id"));
    KnowledgeBaseResponse response = KnowledgeBaseAssembler.toResponse(kb);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @PutMapping("/knowledge-bases/{id}")
  public ResponseEntity<KnowledgeBaseResponse> update(
      @PathVariable String id, @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
    KnowledgeBase kb = service.update(ApiValueParser.positiveId(id, "id"), request);
    KnowledgeBaseResponse response = KnowledgeBaseAssembler.toResponse(kb);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @DeleteMapping("/knowledge-bases/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    int version =
        service.softDelete(
            ApiValueParser.positiveId(id, "id"), ApiValueParser.requiredStrongEtagVersion(ifMatch));
    return ResponseEntity.noContent().eTag(ApiValueParser.strongEtag(version)).build();
  }

  @PutMapping("/knowledge-bases/{id}/disable")
  public ResponseEntity<Void> disable(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    int version =
        service.disable(
            ApiValueParser.positiveId(id, "id"), ApiValueParser.requiredStrongEtagVersion(ifMatch));
    return ResponseEntity.noContent().eTag(ApiValueParser.strongEtag(version)).build();
  }

  @PutMapping("/knowledge-bases/{id}/enable")
  public ResponseEntity<Void> enable(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    int version =
        service.enable(
            ApiValueParser.positiveId(id, "id"), ApiValueParser.requiredStrongEtagVersion(ifMatch));
    return ResponseEntity.noContent().eTag(ApiValueParser.strongEtag(version)).build();
  }
}
