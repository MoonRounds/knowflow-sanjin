package knowflow.sanjin.modules.conversation.assembler;

import java.util.List;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.vo.MessageResponse;

public final class MessageAssembler {

  private MessageAssembler() {}

  public static MessageResponse toResponse(ChatMessage m) {
    MessageResponse r = new MessageResponse();
    r.setId(m.getId().toString());
    r.setConversationId(m.getConversationId().toString());
    r.setRole(m.getRole());
    r.setContent(m.getContent());
    if (m.getReplyToMessageId() != null) {
      r.setReplyToMessageId(m.getReplyToMessageId().toString());
    }
    r.setGenerationStatus(m.getGenerationStatus());
    r.setActive(m.getIsActive() != null && m.getIsActive());
    if (m.getModelConfigId() != null) {
      r.setModelConfigId(m.getModelConfigId().toString());
    }
    r.setRevisionNo(m.getRevisionNo());
    r.setModelName(m.getModelName());
    r.setProviderName(m.getProviderName());
    r.setErrorCode(m.getErrorCode());
    if (m.getUsageTotalTokens() != null
        || m.getUsagePromptTokens() != null
        || m.getUsageCompletionTokens() != null) {
      MessageResponse.TokenUsage usage = new MessageResponse.TokenUsage();
      usage.setPromptTokens(m.getUsagePromptTokens());
      usage.setCompletionTokens(m.getUsageCompletionTokens());
      usage.setTotalTokens(m.getUsageTotalTokens());
      r.setUsage(usage);
    }
    r.setCreatedAt(m.getCreatedAt());
    r.setUpdatedAt(m.getUpdatedAt());
    return r;
  }

  public static List<MessageResponse> toResponseList(List<ChatMessage> list) {
    return list.stream().map(MessageAssembler::toResponse).toList();
  }
}
