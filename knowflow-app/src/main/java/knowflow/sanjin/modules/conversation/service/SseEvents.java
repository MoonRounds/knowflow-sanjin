package knowflow.sanjin.modules.conversation.service;

import java.util.List;

/** Phase 3 SSE 协议事件名与负载。事件携带协议版本与字符串 ID，不透传 Provider 原始事件。 */
public final class SseEvents {

  public static final String PROTOCOL_VERSION = "1";

  private SseEvents() {}

  public static final String STARTED = "generation.started";
  public static final String STAGE = "generation.stage";
  public static final String DELTA = "content.delta";
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

  /** generation.completed 负载：完整内容、active 标记与 token usage。 */
  public record CompletedEvent(
      String protocolVersion,
      String assistantMessageId,
      String content,
      boolean active,
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
    return List.of(STARTED, STAGE, DELTA, COMPLETED, FAILED);
  }
}
