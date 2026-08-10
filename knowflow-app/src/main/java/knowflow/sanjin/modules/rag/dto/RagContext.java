package knowflow.sanjin.modules.rag.dto;

import java.util.List;

/**
 * 一次 Generation 的 RAG 结果（在流式前同步构造）：注入材料、状态与完整 trace。
 *
 * <p>{@code ragStatus} 为 NOT_AVAILABLE / NOT_NEEDED / USED / NO_RELEVANT_CONTEXT / DEGRADED。 {@code
 * injectedText} 是以 {@code [Sx]} 编号并标记不可信引用区的材料；{@code sources} 供 SSE 与 trace 使用。
 */
public class RagContext {

  private String ragStatus;

  private String injectedText;

  private List<RetrievedSource> sources;

  private RouterTrace routerTrace;

  private RetrievalTrace retrievalTrace;

  public static RagContext simple(String ragStatus) {
    RagContext ctx = new RagContext();
    ctx.setRagStatus(ragStatus);
    ctx.setSources(List.of());
    return ctx;
  }

  public String getRagStatus() {
    return ragStatus;
  }

  public void setRagStatus(String ragStatus) {
    this.ragStatus = ragStatus;
  }

  public String getInjectedText() {
    return injectedText;
  }

  public void setInjectedText(String injectedText) {
    this.injectedText = injectedText;
  }

  public List<RetrievedSource> getSources() {
    return sources;
  }

  public void setSources(List<RetrievedSource> sources) {
    this.sources = sources;
  }

  public RouterTrace getRouterTrace() {
    return routerTrace;
  }

  public void setRouterTrace(RouterTrace routerTrace) {
    this.routerTrace = routerTrace;
  }

  public RetrievalTrace getRetrievalTrace() {
    return retrievalTrace;
  }

  public void setRetrievalTrace(RetrievalTrace retrievalTrace) {
    this.retrievalTrace = retrievalTrace;
  }
}
