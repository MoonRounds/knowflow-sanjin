package knowflow.sanjin.modules.extraction.controller;

import jakarta.validation.Valid;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.extraction.dto.UpdateCandidateDraftRequest;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.service.CandidateConfirmService;
import knowflow.sanjin.modules.extraction.service.CandidateService;
import knowflow.sanjin.modules.extraction.vo.CandidatePageResponse;
import knowflow.sanjin.modules.extraction.vo.CandidateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** KnowledgeCandidate 审核 REST 入口：列表、详情、草稿编辑、拒绝/恢复与幂等确认。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class CandidateController {

  private final CandidateService candidateService;
  private final CandidateConfirmService confirmService;

  public CandidateController(
      CandidateService candidateService, CandidateConfirmService confirmService) {
    this.candidateService = candidateService;
    this.confirmService = confirmService;
  }

  @GetMapping("/candidates")
  public CandidatePageResponse list(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "page", defaultValue = "1") long page,
      @RequestParam(name = "size", defaultValue = "20") long size) {
    return CandidateAssembler.toPage(
        candidateService.listForOwner(status, page, size), this::toResponse);
  }

  @GetMapping("/candidates/{id}")
  public ResponseEntity<CandidateResponse> get(@PathVariable String id) {
    KnowledgeCandidate candidate =
        candidateService.getByIdAndOwner(ApiValueParser.positiveId(id, "id"));
    CandidateResponse response = toResponse(candidate);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @PutMapping("/candidates/{id}/draft")
  public ResponseEntity<CandidateResponse> updateDraft(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody UpdateCandidateDraftRequest request) {
    request.setRowVersion(ApiValueParser.requiredStrongEtagVersion(ifMatch));
    KnowledgeCandidate updated =
        candidateService.updateDraft(ApiValueParser.positiveId(id, "id"), request);
    CandidateResponse response = toResponse(updated);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @PostMapping("/candidates/{id}/reject")
  public ResponseEntity<CandidateResponse> reject(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    KnowledgeCandidate updated =
        candidateService.reject(
            ApiValueParser.positiveId(id, "id"), ApiValueParser.requiredStrongEtagVersion(ifMatch));
    CandidateResponse response = toResponse(updated);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @PostMapping("/candidates/{id}/restore")
  public ResponseEntity<CandidateResponse> restore(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    KnowledgeCandidate updated =
        candidateService.restore(
            ApiValueParser.positiveId(id, "id"), ApiValueParser.requiredStrongEtagVersion(ifMatch));
    CandidateResponse response = toResponse(updated);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  @PostMapping("/candidates/{id}/confirm")
  public ResponseEntity<CandidateResponse> confirm(@PathVariable String id) {
    KnowledgeCandidate confirmed = confirmService.confirm(ApiValueParser.positiveId(id, "id"));
    CandidateResponse response = toResponse(confirmed);
    return ResponseEntity.ok()
        .eTag(ApiValueParser.strongEtag(response.getRowVersion()))
        .body(response);
  }

  private CandidateResponse toResponse(KnowledgeCandidate candidate) {
    return CandidateAssembler.toResponse(candidate, confirmService);
  }
}
