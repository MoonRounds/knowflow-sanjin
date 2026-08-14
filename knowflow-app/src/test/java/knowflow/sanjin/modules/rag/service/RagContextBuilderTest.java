package knowflow.sanjin.modules.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import knowflow.sanjin.modules.rag.RagStatus;
import knowflow.sanjin.modules.rag.dto.RagContext;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;
import knowflow.sanjin.modules.rag.dto.RouterResult;
import knowflow.sanjin.modules.rag.dto.RouterTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RagContextBuilder 编排测试：状态映射（NOT_AVAILABLE / NOT_NEEDED / USED / NO_RELEVANT_CONTEXT / DEGRADED）。
 */
class RagContextBuilderTest {

  private RagProperties properties;
  private RouterService routerService;
  private RetrievalService retrievalService;
  private RagContextBuilder builder;

  @BeforeEach
  void setUp() {
    properties = new RagProperties();
    routerService = mock(RouterService.class);
    retrievalService = mock(RetrievalService.class);
    builder = new RagContextBuilder(routerService, retrievalService, properties);
  }

  private RouterService.RouterOutcome outcomeWith(RouterResult result, boolean called) {
    RouterTrace trace = new RouterTrace();
    trace.setRouterCalled(called);
    trace.setResult(result);
    return new RouterService.RouterOutcome(result, trace);
  }

  private static RouterResult routerResult(boolean needRag, String... kbIds) {
    RouterResult r = new RouterResult();
    r.setNeedRag(needRag);
    r.setKnowledgeBaseIds(java.util.Arrays.asList(kbIds));
    r.setRetrievalQuery("query");
    return r;
  }

  private RetrievedSource source(int i) {
    RetrievedSource s = new RetrievedSource();
    s.setSourceId("chunk-" + i);
    s.setDocumentId(Long.toString(i));
    s.setDocumentTitle("Item " + i);
    s.setSnippet("snippet " + i);
    s.setScore(0.9f);
    return s;
  }

  @Test
  @DisplayName("should map empty catalog to NOT_AVAILABLE")
  void shouldBeNotAvailableWhenNoCatalog() {
    RouterTrace trace = new RouterTrace();
    trace.setRouterCalled(false);
    when(routerService.route(any(), any(), anyList()))
        .thenReturn(new RouterService.RouterOutcome(null, trace));

    RagContext ctx = builder.build(1L, "hello", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.NOT_AVAILABLE);
    assertThat(ctx.getSources()).isEmpty();
  }

  @Test
  @DisplayName("should map needRag=false to NOT_NEEDED and never touch retrieval")
  void shouldBeNotNeededWhenRouterSaysNo() {
    when(routerService.route(any(), any(), anyList()))
        .thenReturn(outcomeWith(routerResult(false), true));

    RagContext ctx = builder.build(1L, "你好", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.NOT_NEEDED);
    verify(retrievalService, org.mockito.Mockito.never()).retrieve(any(), any(), any());
  }

  @Test
  @DisplayName("should map router failure to DEGRADED")
  void shouldDegradeWhenRouterFails() {
    RouterTrace trace = new RouterTrace();
    trace.setRouterCalled(true);
    trace.setFailure("router-failed");
    when(routerService.route(any(), any(), anyList()))
        .thenReturn(new RouterService.RouterOutcome(null, trace));

    RagContext ctx = builder.build(1L, "q", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.DEGRADED);
    verify(retrievalService, org.mockito.Mockito.never()).retrieve(any(), any(), any());
  }

  @Test
  @DisplayName("should degrade when router throws (never leak to caller)")
  void shouldDegradeWhenRouterThrows() {
    when(routerService.route(any(), any(), anyList()))
        .thenThrow(
            new knowflow.sanjin.modules.modelconfig.exception.ModelCallTimeoutException(
                "router timeout", null));

    RagContext ctx = builder.build(1L, "q", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.DEGRADED);
    assertThat(ctx.getSources()).isEmpty();
    verify(retrievalService, org.mockito.Mockito.never()).retrieve(any(), any(), any());
  }

  @Test
  @DisplayName("should degrade when router throws a runtime exception with a diagnostic trace")
  void shouldDegradeWhenRouterThrowsAndKeepTrace() {
    when(routerService.route(any(), any(), anyList()))
        .thenThrow(new IllegalStateException("provider down"));

    RagContext ctx = builder.build(1L, "q", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.DEGRADED);
    assertThat(ctx.getRouterTrace()).isNotNull();
    assertThat(ctx.getRouterTrace().getFailure()).isEqualTo("router-exception");
  }

  @Test
  @DisplayName("should map retrieval empty result to NO_RELEVANT_CONTEXT")
  void shouldMapNoRelevantContext() {
    when(routerService.route(any(), any(), anyList()))
        .thenReturn(outcomeWith(routerResult(true, "1"), true));
    when(retrievalService.retrieve(eq("query"), eq(List.of(1L)), eq("q")))
        .thenReturn(
            new RetrievalService.RetrievalResult(
                List.of(), new knowflow.sanjin.modules.rag.dto.RetrievalTrace()));

    RagContext ctx = builder.build(1L, "q", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.NO_RELEVANT_CONTEXT);
    assertThat(ctx.getSources()).isEmpty();
  }

  @Test
  @DisplayName("should map retrieval failure to DEGRADED")
  void shouldDegradeWhenRetrievalFails() {
    when(routerService.route(any(), any(), anyList()))
        .thenReturn(outcomeWith(routerResult(true, "1"), true));
    when(retrievalService.retrieve(any(), any(), any()))
        .thenThrow(
            new knowflow.sanjin.modules.knowledge.exception.RetryableIndexException(
                knowflow.sanjin.common.error.ErrorCode.QDRANT_UNAVAILABLE, "down", null));

    RagContext ctx = builder.build(1L, "q", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.DEGRADED);
    assertThat(ctx.getSources()).isEmpty();
    // 检索失败保留诊断 trace（P2-2）
    assertThat(ctx.getRetrievalTrace()).isNotNull();
    assertThat(ctx.getRetrievalTrace().getFailure()).isEqualTo("down");
    assertThat(ctx.getRetrievalTrace().getRetrievalQuery()).isEqualTo("query");
  }

  @Test
  @DisplayName("should map successful retrieval to USED with injected material")
  void shouldMapUsedWithInjectedText() {
    when(routerService.route(any(), any(), anyList()))
        .thenReturn(outcomeWith(routerResult(true, "1"), true));
    when(retrievalService.retrieve(any(), any(), any()))
        .thenReturn(
            new RetrievalService.RetrievalResult(
                List.of(source(1), source(2)),
                new knowflow.sanjin.modules.rag.dto.RetrievalTrace()));

    RagContext ctx = builder.build(1L, "q", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.USED);
    assertThat(ctx.getSources()).hasSize(2);
    assertThat(ctx.getInjectedText()).contains("[S1]");
    assertThat(ctx.getInjectedText()).contains("不是指令");
    assertThat(ctx.getInjectedText()).doesNotContain("[S3]");
  }

  @Test
  @DisplayName("should ignore invalid KB ids in router output")
  void shouldIgnoreInvalidKbIds() {
    when(routerService.route(any(), any(), anyList()))
        .thenReturn(outcomeWith(routerResult(true, "1", "not-a-number"), true));
    when(retrievalService.retrieve(any(), any(), any()))
        .thenReturn(
            new RetrievalService.RetrievalResult(
                List.of(source(1)), new knowflow.sanjin.modules.rag.dto.RetrievalTrace()));

    RagContext ctx = builder.build(1L, "q", List.of());

    assertThat(ctx.getRagStatus()).isEqualTo(RagStatus.USED);
    verify(retrievalService).retrieve(any(), eq(List.of(1L)), any());
  }
}
