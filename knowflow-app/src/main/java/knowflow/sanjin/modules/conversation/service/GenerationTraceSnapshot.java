package knowflow.sanjin.modules.conversation.service;

import java.util.List;
import knowflow.sanjin.modules.rag.dto.RagContext;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;

/**
 * 一次 Generation 生命周期中携带的 RAG 快照：流式前由 {@link RagContext} 构造，finalizer 随消息状态落库。
 *
 * <p>failed/cancelled 时保存「已执行到哪一步」的状态与已检索 sources，但不含 cited（无完整文本）。
 */
public class GenerationTraceSnapshot {

  private final String ragStatus;
  private final List<RetrievedSource> sources;
  private final knowflow.sanjin.modules.rag.dto.RouterTrace routerTrace;
  private final knowflow.sanjin.modules.rag.dto.RetrievalTrace retrievalTrace;

  public GenerationTraceSnapshot(
      String ragStatus,
      List<RetrievedSource> sources,
      knowflow.sanjin.modules.rag.dto.RouterTrace routerTrace,
      knowflow.sanjin.modules.rag.dto.RetrievalTrace retrievalTrace) {
    this.ragStatus = ragStatus;
    this.sources = sources;
    this.routerTrace = routerTrace;
    this.retrievalTrace = retrievalTrace;
  }

  public static GenerationTraceSnapshot from(RagContext rag) {
    if (rag == null) {
      return null;
    }
    return new GenerationTraceSnapshot(
        rag.getRagStatus(), rag.getSources(), rag.getRouterTrace(), rag.getRetrievalTrace());
  }

  public String ragStatus() {
    return ragStatus;
  }

  public List<RetrievedSource> sources() {
    return sources != null ? sources : List.of();
  }

  public knowflow.sanjin.modules.rag.dto.RouterTrace routerTrace() {
    return routerTrace;
  }

  public knowflow.sanjin.modules.rag.dto.RetrievalTrace retrievalTrace() {
    return retrievalTrace;
  }
}
