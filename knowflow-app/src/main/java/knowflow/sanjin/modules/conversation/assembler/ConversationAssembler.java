package knowflow.sanjin.modules.conversation.assembler;

import java.util.List;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.vo.ConversationResponse;

public final class ConversationAssembler {

  private ConversationAssembler() {}

  public static ConversationResponse toResponse(Conversation c) {
    ConversationResponse r = new ConversationResponse();
    r.setId(c.getId().toString());
    r.setTitle(c.getTitle());
    if (c.getDefaultModelConfigId() != null) {
      r.setDefaultModelConfigId(c.getDefaultModelConfigId().toString());
    }
    if (c.getActiveGenerationMessageId() != null) {
      r.setActiveGenerationMessageId(c.getActiveGenerationMessageId().toString());
    }
    r.setRowVersion(c.getRowVersion() != null ? c.getRowVersion() : 0);
    r.setCreatedAt(c.getCreatedAt());
    r.setUpdatedAt(c.getUpdatedAt());
    return r;
  }

  public static List<ConversationResponse> toResponseList(List<Conversation> list) {
    return list.stream().map(ConversationAssembler::toResponse).toList();
  }
}
