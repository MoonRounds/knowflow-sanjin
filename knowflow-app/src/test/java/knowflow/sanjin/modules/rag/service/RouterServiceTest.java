package knowflow.sanjin.modules.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import knowflow.sanjin.modules.conversation.memory.MemoryService;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.rag.dto.RoutableKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** RouterService 单元测试：目录构建、0/1/多知识库、非法 ID、一次修复与失败降级。 */
class RouterServiceTest {

  private static final long OWNER_ID = 1L;

  private RagProperties properties;
  private CurrentOwnerProvider ownerProvider;
  private KnowledgeBaseMapper kbMapper;
  private KnowledgeDocumentMapper itemMapper;
  private ModelConfigService modelConfigService;
  private ModelClientFactory clientFactory;
  private MemoryService memoryService;
  private RouterService routerService;
  private ChatModel chatModel;

  @BeforeEach
  void setUp() {
    properties = new RagProperties();
    ownerProvider = mock(CurrentOwnerProvider.class);
    when(ownerProvider.getCurrentOwnerId()).thenReturn(OWNER_ID);
    kbMapper = mock(KnowledgeBaseMapper.class);
    itemMapper = mock(KnowledgeDocumentMapper.class);
    modelConfigService = mock(ModelConfigService.class);
    clientFactory = mock(ModelClientFactory.class);
    memoryService = mock(MemoryService.class);
    when(memoryService.loadWindow(any())).thenReturn(List.of());
    when(memoryService.loadWindow(anyLong())).thenReturn(List.of());

    chatModel = mock(ChatModel.class);
    when(clientFactory.create(any(ModelConfigRevision.class))).thenReturn(chatModel);
    when(clientFactory.callWithTotalTimeout(any(Supplier.class), anyLong()))
        .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
    when(clientFactory.extractText(any(ChatResponse.class)))
        .thenAnswer(inv -> ((ChatResponse) inv.getArgument(0)).getResult().getOutput().getText());

    ModelConfigRevision rev = new ModelConfigRevision();
    rev.setId(7L);
    when(modelConfigService.resolveUtilityRevisionForRouting()).thenReturn(rev);

    routerService =
        new RouterService(
            properties,
            ownerProvider,
            kbMapper,
            itemMapper,
            modelConfigService,
            clientFactory,
            memoryService);
  }

  private KnowledgeBase kb(long id) {
    KnowledgeBase kb = new KnowledgeBase();
    kb.setId(id);
    kb.setOwnerId(OWNER_ID);
    kb.setDisplayName("KB " + id);
    kb.setEnabled(true);
    kb.setDeleted(false);
    return kb;
  }

  private KnowledgeDocument document(long id, long kbId) {
    KnowledgeDocument doc = new KnowledgeDocument();
    doc.setId(id);
    doc.setOwnerId(OWNER_ID);
    doc.setKbId(kbId);
    doc.setDeleted(false);
    doc.setIndexedVersion(1);
    return doc;
  }

  /** 每个 KB 下都有一个已索引 Document（kbId 单归属）→ 全部可路由。 */
  private void stubCatalog(long... kbIds) {
    List<KnowledgeBase> kbs = java.util.Arrays.stream(kbIds).mapToObj(this::kb).toList();
    when(kbMapper.selectList(any())).thenReturn(kbs);
    List<KnowledgeDocument> docs =
        java.util.Arrays.stream(kbIds).mapToObj(id -> document(id, id)).toList();
    when(itemMapper.selectList(any())).thenReturn(docs);
  }

  private void stubRouterJson(String json) {
    ChatResponse response =
        ChatResponse.builder()
            .generations(List.of(new Generation(new AssistantMessage(json))))
            .build();
    when(chatModel.call(any(Prompt.class))).thenReturn(response);
  }

  private void stubRouterJson(String... jsons) {
    AtomicInteger n = new AtomicInteger();
    when(chatModel.call(any(Prompt.class)))
        .thenAnswer(
            inv -> {
              int i = Math.min(n.getAndIncrement(), jsons.length - 1);
              return ChatResponse.builder()
                  .generations(List.of(new Generation(new AssistantMessage(jsons[i]))))
                  .build();
            });
  }

  private static String routerJson(boolean needRag, String... kbIds) {
    String ids =
        java.util.Arrays.stream(kbIds)
            .map(id -> "\"" + id + "\"")
            .collect(java.util.stream.Collectors.joining(","));
    return "{\"needRag\":"
        + needRag
        + ",\"knowledgeBaseIds\":["
        + ids
        + "],\"retrievalQuery\":\"q\",\"routeScores\":[]}";
  }

  @Test
  @DisplayName("should return not-available when catalog is empty (no model call)")
  void shouldBeNotAvailableWhenNoCatalog() {
    when(kbMapper.selectList(any())).thenReturn(List.of());

    RouterService.RouterOutcome outcome = routerService.route(1L, "hello");

    assertThat(outcome.available()).isFalse();
    assertThat(outcome.trace().isRouterCalled()).isFalse();
    assertThat(outcome.trace().getCatalog()).isEmpty();
    assertThat(outcome.trace().getMode()).isEqualTo(RouterService.MODE_AUTO);
  }

  @Test
  @DisplayName("should skip model call and be not-available when utility is not configured")
  void shouldBeNotAvailableWhenUtilityMissing() {
    stubCatalog(1L);
    when(modelConfigService.resolveUtilityRevisionForRouting())
        .thenThrow(
            new knowflow.sanjin.modules.modelconfig.exception.ModelConfigNotFoundException(null));

    RouterService.RouterOutcome outcome = routerService.route(1L, "hello");

    assertThat(outcome.available()).isFalse();
    assertThat(outcome.trace().isRouterCalled()).isFalse();
  }

  @Test
  @DisplayName("should return NOT_NEEDED decision for a greeting")
  void shouldReturnNeedRagFalse() {
    stubCatalog(1L);
    stubRouterJson(routerJson(false));

    RouterService.RouterOutcome outcome = routerService.route(1L, "你好");

    assertThat(outcome.available()).isTrue();
    assertThat(outcome.result().isNeedRag()).isFalse();
    assertThat(outcome.trace().getResult().isNeedRag()).isFalse();
  }

  @Test
  @DisplayName("should route to a single knowledge base")
  void shouldRouteToSingleKb() {
    stubCatalog(1L, 2L);
    stubRouterJson(routerJson(true, "1"));

    RouterService.RouterOutcome outcome = routerService.route(1L, "What is the deploy checklist?");

    assertThat(outcome.available()).isTrue();
    assertThat(outcome.result().isNeedRag()).isTrue();
    assertThat(outcome.result().getKnowledgeBaseIds()).containsExactly("1");
  }

  @Test
  @DisplayName("should route to multiple knowledge bases")
  void shouldRouteToMultipleKbs() {
    stubCatalog(1L, 2L, 3L);
    stubRouterJson(routerJson(true, "1", "2"));

    RouterService.RouterOutcome outcome = routerService.route(1L, "project conventions");

    assertThat(outcome.result().getKnowledgeBaseIds()).containsExactly("1", "2");
  }

  @Test
  @DisplayName("should fix once when the first output is invalid (too many KBs)")
  void shouldFixOnceWhenOutputInvalid() {
    stubCatalog(1L, 2L);
    // 第一次输出 4 个 KB（超过上限 3）→ 修复后返回 0 个
    stubRouterJson(routerJson(true, "1", "2", "3", "4"), routerJson(false));

    RouterService.RouterOutcome outcome = routerService.route(1L, "q");

    assertThat(outcome.available()).isTrue();
    assertThat(outcome.trace().isFixed()).isTrue();
    assertThat(outcome.result().isNeedRag()).isFalse();
  }

  @Test
  @DisplayName("should fix once when the first output contains an unknown KB id")
  void shouldFixOnceWhenUnknownKbId() {
    stubCatalog(1L, 2L);
    // 第一次输出 id 999（不在目录）→ 修复后返回合法 id
    stubRouterJson(routerJson(true, "999"), routerJson(true, "2"));

    RouterService.RouterOutcome outcome = routerService.route(1L, "q");

    assertThat(outcome.trace().isFixed()).isTrue();
    assertThat(outcome.result().getKnowledgeBaseIds()).containsExactly("2");
  }

  @Test
  @DisplayName("should degrade when output stays invalid after the single fix")
  void shouldDegradeWhenStillInvalidAfterFix() {
    stubCatalog(1L);
    // 两次都返回未知 id
    stubRouterJson(routerJson(true, "999"), routerJson(true, "888"));

    RouterService.RouterOutcome outcome = routerService.route(1L, "q");

    assertThat(outcome.available()).isFalse();
    assertThat(outcome.trace().isRouterCalled()).isTrue();
    assertThat(outcome.trace().getFailure()).isNotBlank();
  }

  @Test
  @DisplayName("should degrade when router output is not parseable JSON")
  void shouldDegradeOnUnparseableOutput() {
    stubCatalog(1L);
    stubRouterJson("not json at all", "still not json");

    RouterService.RouterOutcome outcome = routerService.route(1L, "q");

    assertThat(outcome.available()).isFalse();
  }

  @Test
  @DisplayName("should cap the catalog to the configured limit")
  void shouldCapCatalog() {
    properties.setCatalogLimit(2);
    stubCatalog(1L, 2L, 3L);
    stubRouterJson(routerJson(false));

    RouterService.RouterOutcome outcome = routerService.route(1L, "q");

    assertThat(outcome.trace().getCatalog()).hasSize(2);
    assertThat(outcome.available()).isTrue();
  }

  @Test
  @DisplayName("manual mode keeps all bound routable KBs and rejects out-of-scope output")
  void shouldConstrainManualCatalogWithoutAutoLimit() {
    properties.setCatalogLimit(1);
    stubCatalog(1L, 2L, 3L);
    stubRouterJson(routerJson(true, "3"), routerJson(true, "2"));

    RouterService.RouterOutcome outcome = routerService.route(1L, "q", List.of(1L, 2L));

    assertThat(outcome.trace().getMode()).isEqualTo(RouterService.MODE_MANUAL);
    assertThat(outcome.trace().getCatalog())
        .extracting(RoutableKnowledgeBase::getId)
        .containsExactly(1L, 2L);
    assertThat(outcome.trace().isFixed()).isTrue();
    assertThat(outcome.result().getKnowledgeBaseIds()).containsExactly("2");
  }

  @Test
  @DisplayName("manual mode with no currently routable binding stays manual and unavailable")
  void shouldStayManualWhenAllBindingsUnavailable() {
    when(kbMapper.selectList(any())).thenReturn(List.of());

    RouterService.RouterOutcome outcome = routerService.route(1L, "q", List.of(99L));

    assertThat(outcome.available()).isFalse();
    assertThat(outcome.trace().getMode()).isEqualTo(RouterService.MODE_MANUAL);
    assertThat(outcome.trace().getCatalog()).isEmpty();
  }

  @Test
  @DisplayName("should include only routable KBs in the catalog (no indexed document excluded)")
  void shouldExcludeKbWithoutRoutableItem() {
    // 仅 KB 1 有已索引 Document；KB 2 无 Document → 目录只含 KB 1
    when(kbMapper.selectList(any())).thenReturn(List.of(kb(1L), kb(2L)));
    when(itemMapper.selectList(any())).thenReturn(List.of(document(10L, 1L)));

    stubRouterJson(routerJson(false));
    RouterService.RouterOutcome outcome = routerService.route(1L, "q");

    List<RoutableKnowledgeBase> catalog = outcome.trace().getCatalog();
    assertThat(catalog).hasSize(1);
    assertThat(catalog.get(0).getId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("should return empty catalog when enabled KBs have no routable item (no crash)")
  void shouldReturnEmptyCatalogWhenNoRoutableItem() {
    when(kbMapper.selectList(any())).thenReturn(List.of(kb(1L)));
    // 该 KB 下没有任何未软删已索引 Document → 目录为空
    when(itemMapper.selectList(any())).thenReturn(List.of());

    RouterService.RouterOutcome outcome = routerService.route(1L, "q");

    assertThat(outcome.available()).isFalse();
    assertThat(outcome.trace().getCatalog()).isEmpty();
  }
}
