package knowflow.sanjin.modules.knowledgebase.assembler;

import java.util.List;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.vo.KnowledgeBaseResponse;

public final class KnowledgeBaseAssembler {

  private KnowledgeBaseAssembler() {}

  public static KnowledgeBaseResponse toResponse(KnowledgeBase kb) {
    KnowledgeBaseResponse r = new KnowledgeBaseResponse();
    r.setId(kb.getId().toString());
    r.setName(kb.getDisplayName());
    r.setDescription(kb.getDescription());
    r.setEnabled(kb.getEnabled() != null && kb.getEnabled());
    r.setRowVersion(kb.getRowVersion() != null ? kb.getRowVersion() : 0);
    r.setCreatedAt(kb.getCreatedAt());
    r.setUpdatedAt(kb.getUpdatedAt());
    return r;
  }

  public static List<KnowledgeBaseResponse> toResponseList(List<KnowledgeBase> list) {
    return list.stream().map(KnowledgeBaseAssembler::toResponse).toList();
  }
}
