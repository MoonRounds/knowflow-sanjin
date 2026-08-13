package knowflow.sanjin.modules.knowledge.assembler;

import java.util.List;
import java.util.Map;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.vo.KnowledgeDocumentResponse;
import knowflow.sanjin.modules.knowledge.vo.KnowledgeDocumentSummaryResponse;

/** Entity 与 API 模型的显式转换；关联关系由 Service 批量组装后传入。 */
public final class KnowledgeDocumentAssembler {

  private KnowledgeDocumentAssembler() {}

  public static KnowledgeDocumentResponse toResponse(
      KnowledgeDocument document, Long knowledgeBaseId, List<String> tags) {
    KnowledgeDocumentResponse r = new KnowledgeDocumentResponse();
    r.setId(document.getId().toString());
    r.setSourceType(document.getSourceType());
    r.setTitle(document.getTitle());
    r.setSummary(document.getSummary());
    r.setContent(document.getContent());
    r.setContentVersion(document.getContentVersion());
    r.setIndexedVersion(document.getIndexedVersion());
    r.setIndexStatus(document.getIndexStatus());
    r.setIndexErrorCode(document.getIndexErrorCode());
    r.setIndexErrorMessage(document.getIndexErrorMessage());
    r.setKnowledgeBaseId(knowledgeBaseId == null ? null : String.valueOf(knowledgeBaseId));
    r.setTags(tags);
    r.setRowVersion(document.getRowVersion());
    r.setCreatedAt(document.getCreatedAt());
    r.setUpdatedAt(document.getUpdatedAt());
    return r;
  }

  public static List<KnowledgeDocumentResponse> toResponseList(
      List<KnowledgeDocument> documents,
      Map<Long, Long> kbIdByDocument,
      Map<Long, List<String>> tagsByDocument) {
    return documents.stream()
        .map(
            document ->
                toResponse(
                    document,
                    kbIdByDocument.get(document.getId()),
                    tagsByDocument.getOrDefault(document.getId(), List.of())))
        .toList();
  }

  /** 列表摘要：剥离 content/summary/索引错误字段（错误码聚合展示属 P4）。 */
  public static KnowledgeDocumentSummaryResponse toSummary(
      KnowledgeDocument document, Long knowledgeBaseId, List<String> tags) {
    KnowledgeDocumentSummaryResponse r = new KnowledgeDocumentSummaryResponse();
    r.setId(document.getId().toString());
    r.setSourceType(document.getSourceType());
    r.setTitle(document.getTitle());
    r.setContentVersion(document.getContentVersion());
    r.setIndexedVersion(document.getIndexedVersion());
    r.setIndexStatus(document.getIndexStatus());
    r.setKnowledgeBaseId(knowledgeBaseId == null ? null : String.valueOf(knowledgeBaseId));
    r.setTags(tags);
    r.setRowVersion(document.getRowVersion());
    r.setCreatedAt(document.getCreatedAt());
    r.setUpdatedAt(document.getUpdatedAt());
    return r;
  }

  public static List<KnowledgeDocumentSummaryResponse> toSummaryList(
      List<KnowledgeDocument> documents,
      Map<Long, Long> kbIdByDocument,
      Map<Long, List<String>> tagsByDocument) {
    return documents.stream()
        .map(
            document ->
                toSummary(
                    document,
                    kbIdByDocument.get(document.getId()),
                    tagsByDocument.getOrDefault(document.getId(), List.of())))
        .toList();
  }
}
