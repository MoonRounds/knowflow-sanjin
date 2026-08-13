package knowflow.sanjin.modules.knowledge.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.knowledge.assembler.KnowledgeDocumentAssembler;
import knowflow.sanjin.modules.knowledge.dto.CreateDocumentRequest;
import knowflow.sanjin.modules.knowledge.dto.UpdateDocumentRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.service.KnowledgeDocumentService;
import knowflow.sanjin.modules.knowledge.vo.KnowledgeDocumentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Manual Note REST 入口：BIGINT id 走字符串路径参数，写操作通过 If-Match/ETag 传递乐观锁版本。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class KnowledgeDocumentController {

  private final KnowledgeDocumentService service;

  public KnowledgeDocumentController(KnowledgeDocumentService service) {
    this.service = service;
  }

  @PostMapping("/documents")
  public ResponseEntity<KnowledgeDocumentResponse> createManualNote(
      @Valid @RequestBody CreateDocumentRequest request) {
    KnowledgeDocument document = service.createManualNote(request);
    KnowledgeDocumentResponse response = toResponse(document);
    return ResponseEntity.created(URI.create("/api/v1/documents/" + response.getId()))
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @GetMapping("/documents")
  public List<KnowledgeDocumentResponse> list() {
    List<KnowledgeDocument> documents = service.listForOwner();
    List<Long> documentIds = documents.stream().map(KnowledgeDocument::getId).toList();
    Map<Long, Long> kbIds = service.batchKnowledgeBaseId(documentIds);
    Map<Long, List<String>> tags = service.batchTagNames(documentIds);
    return KnowledgeDocumentAssembler.toResponseList(documents, kbIds, tags);
  }

  @GetMapping("/documents/{id}")
  public ResponseEntity<KnowledgeDocumentResponse> get(@PathVariable String id) {
    KnowledgeDocument document = service.getByIdAndOwner(ApiValueParser.positiveId(id, "id"));
    KnowledgeDocumentResponse response = toResponse(document);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @PutMapping("/documents/{id}")
  public ResponseEntity<KnowledgeDocumentResponse> update(
      @PathVariable String id, @Valid @RequestBody UpdateDocumentRequest request) {
    KnowledgeDocument document =
        service.updateManualNote(ApiValueParser.positiveId(id, "id"), request);
    KnowledgeDocumentResponse response = toResponse(document);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @DeleteMapping("/documents/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    service.softDelete(
        ApiValueParser.positiveId(id, "id"), ApiValueParser.requiredStrongEtagVersion(ifMatch));
    return ResponseEntity.noContent().build();
  }

  private KnowledgeDocumentResponse toResponse(KnowledgeDocument document) {
    return KnowledgeDocumentAssembler.toResponse(
        document,
        service.getKnowledgeBaseId(document.getId()),
        service.getTagNames(document.getId()));
  }
}
