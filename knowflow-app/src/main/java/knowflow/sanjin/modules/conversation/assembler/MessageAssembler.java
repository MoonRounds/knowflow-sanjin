package knowflow.sanjin.modules.conversation.assembler;

import java.util.List;
import java.util.Map;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.GenerationTrace;
import knowflow.sanjin.modules.conversation.service.GenerationTraceService;
import knowflow.sanjin.modules.conversation.vo.MessageResponse;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;

/** ChatMessage Entity → VO 转换：id 转字符串；usage 仅在任一 token 有值时组装；assistant 消息内嵌 RAG trace 快照。 */
public final class MessageAssembler {

  private MessageAssembler() {}

  public static MessageResponse toResponse(ChatMessage m) {
    return toResponse(m, null, null);
  }

  /** 批量转换并内嵌 trace：assistant 消息从 trace 快照重放当时 sources/cited。 */
  public static List<MessageResponse> toResponseList(
      List<ChatMessage> list,
      Map<Long, GenerationTrace> traces,
      GenerationTraceService traceService) {
    return list.stream().map(m -> toResponse(m, traces, traceService)).toList();
  }

  public static List<MessageResponse> toResponseList(List<ChatMessage> list) {
    return list.stream().map(MessageAssembler::toResponse).toList();
  }

  private static MessageResponse toResponse(
      ChatMessage m, Map<Long, GenerationTrace> traces, GenerationTraceService traceService) {
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
    r.setRagStatus(m.getRagStatus());
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

    // assistant 消息：内嵌 trace 快照（sources + cited），历史重放
    if (ChatMessage.ROLE_ASSISTANT.equals(m.getRole()) && traces != null && traceService != null) {
      GenerationTrace trace = traces.get(m.getId());
      if (trace != null) {
        if (r.getRagStatus() == null) {
          r.setRagStatus(trace.getRagStatus());
        }
        List<RetrievedSource> sources = traceService.parseSources(trace.getSourcesJson());
        r.setSources(sources);
      }
    }
    return r;
  }
}
