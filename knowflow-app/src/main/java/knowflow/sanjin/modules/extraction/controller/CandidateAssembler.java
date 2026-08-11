package knowflow.sanjin.modules.extraction.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.stream.Collectors;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.service.CandidateConfirmService;
import knowflow.sanjin.modules.extraction.vo.CandidatePageResponse;
import knowflow.sanjin.modules.extraction.vo.CandidateResponse;

/** Candidate → 响应映射：AI 原值/草稿并列，KB 与 Tag 由确认服务解析为 id+name 结构。 */
public final class CandidateAssembler {

  private CandidateAssembler() {}

  public static CandidateResponse toResponse(
      KnowledgeCandidate c, CandidateConfirmService confirmService) {
    CandidateResponse r = new CandidateResponse();
    r.setId(String.valueOf(c.getId()));
    r.setExtractionTaskId(String.valueOf(c.getExtractionTaskId()));
    r.setStatus(c.getStatus());
    r.setAiTitle(c.getAiTitle());
    r.setAiSummary(c.getAiSummary());
    r.setAiContent(c.getAiContent());
    r.setAiKnowledgeBaseIds(splitIds(c.getAiKnowledgeBaseIds()));
    r.setAiTags(splitIds(c.getAiTags()));
    r.setAiReason(c.getAiReason());
    r.setDraftTitle(c.getDraftTitle());
    r.setDraftSummary(c.getDraftSummary());
    r.setDraftContent(c.getDraftContent());
    r.setDraftKnowledgeBaseIds(splitIds(c.getDraftKnowledgeBaseIds()));
    r.setDraftTags(splitIds(c.getDraftTags()));
    r.setRowVersion(c.getRowVersion());
    r.setConfirmedItemId(confirmService.findConfirmedItemId(c.getId()));
    return r;
  }

  public static CandidatePageResponse toPage(
      Page<KnowledgeCandidate> page,
      java.util.function.Function<KnowledgeCandidate, CandidateResponse> mapper) {
    List<CandidateResponse> items =
        page.getRecords().stream().map(mapper).collect(Collectors.toList());
    return new CandidatePageResponse(page.getTotal(), page.getCurrent(), page.getSize(), items);
  }

  private static String[] splitIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return new String[0];
    }
    return raw.split(",");
  }
}
