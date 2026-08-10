package knowflow.sanjin.modules.conversation.service;

import java.util.List;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;
import knowflow.sanjin.modules.rag.dto.RouterResult;

/** Phase 6 SSE 协议事件名与负载。事件携带协议版本与字符串 ID，不透传 Provider 原始事件。 */
public final class SseEvents {

  /** Phase 6：新增 sources.available 事件。 */
  public static final String PROTOCOL_VERSION = "2";

  private SseEvents() {}

  public static final String STARTED = "generation.started";
  public static final String STAGE = "generation.stage";
  public static final String DELTA = "content.delta";
  public static final String SOURCES_AVAILABLE = "sources.available";
  public static final String COMPLETED = "generation.completed";
  public static final String FAILED = "generation.failed";

  /** generation.started 负载：版本 + 字符串 assistantMessageId + conversationId。 */
  public record StartedEvent(
      String protocolVersion, String conversationId, String assistantMessageId) {}

  /** generation.stage 负载：模型信息与阶段说明。 */
  public record StageEvent(
      String protocolVersion, String assistantMessageId, String stage, String modelName) {}

  /** content.delta 负载：增量文本。 */
  public record DeltaEvent(String protocolVersion, String assistantMessageId, String delta) {}

  /** sources.available 负载：RAG 状态 + retrieved/cited 来源 + 可选 Router 诊断（completed 前发送）。 */
  public record SourcesAvailableEvent(
      String protocolVersion,
      String assistantMessageId,
      String ragStatus,
      List<RetrievedSource> sources,
      RouterDiagnostic router) {}

  /** Router 诊断（仅发生过路由调用时携带；route score 只用于展示）。 */
  public record RouterDiagnostic(
      boolean needRag,
      List<String> knowledgeBaseIds,
      String retrievalQuery,
      List<RouterResult.RouteScore> routeScores) {}

  /** generation.completed 负载：完整内容、active 标记、RAG 状态与 token usage。 */
  public record CompletedEvent(
      String protocolVersion,
      String assistantMessageId,
      String content,
      boolean active,
      String ragStatus,
      TokenUsage usage) {}

  /** generation.failed 负载：稳定错误码与失败阶段；不含 Provider 原始错误文本。 */
  public record FailedEvent(
      String protocolVersion,
      String assistantMessageId,
      String errorCode,
      String stage,
      String detail) {}

  /** Token Usage 尽力记录，不承诺精确费用。 */
  public record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {}

  public static List<String> eventNames() {
    return List.of(STARTED, STAGE, DELTA, SOURCES_AVAILABLE, COMPLETED, FAILED);
  }
}
