package knowflow.sanjin.modules.knowledge.assembler;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.vo.KnowledgeItemResponse;

/** Entity 与 API 模型的显式转换；关联关系由 Service 批量组装后传入。 */
public final class KnowledgeItemAssembler {

  private KnowledgeItemAssembler() {}

  public static KnowledgeItemResponse toResponse(
      KnowledgeItem item, List<Long> knowledgeBaseIds, List<String> tags) {
    KnowledgeItemResponse r = new KnowledgeItemResponse();
    r.setId(item.getId().toString());
    r.setSourceType(item.getSourceType());
    r.setTitle(item.getTitle());
    r.setSummary(item.getSummary());
    r.setContent(item.getContent());
    r.setContentVersion(item.getContentVersion());
    r.setIndexedVersion(item.getIndexedVersion());
    r.setIndexStatus(item.getIndexStatus());
    r.setIndexErrorCode(item.getIndexErrorCode());
    r.setIndexErrorMessage(item.getIndexErrorMessage());
    r.setKnowledgeBaseIds(
        knowledgeBaseIds.stream().map(String::valueOf).collect(Collectors.toList()));
    r.setTags(tags);
    r.setRowVersion(item.getRowVersion());
    r.setCreatedAt(item.getCreatedAt());
    r.setUpdatedAt(item.getUpdatedAt());
    return r;
  }

  public static List<KnowledgeItemResponse> toResponseList(
      List<KnowledgeItem> items,
      Map<Long, List<Long>> kbIdsByItem,
      Map<Long, List<String>> tagsByItem) {
    return items.stream()
        .map(
            item ->
                toResponse(
                    item,
                    kbIdsByItem.getOrDefault(item.getId(), List.of()),
                    tagsByItem.getOrDefault(item.getId(), List.of())))
        .toList();
  }
}
