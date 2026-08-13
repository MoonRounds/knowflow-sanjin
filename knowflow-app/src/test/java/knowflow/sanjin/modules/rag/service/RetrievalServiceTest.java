package knowflow.sanjin.modules.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.common.config.QdrantProperties;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeBaseItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeChunk;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingClient;
import knowflow.sanjin.modules.knowledge.infrastructure.QdrantClient;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeBaseItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeChunkMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RetrievalService 单元测试：owner 过滤、KB OR filter、MySQL 回查二次校验、幽灵 Point 剔除。 */
class RetrievalServiceTest {

  private static final long OWNER_ID = 1L;

  private RagProperties properties;
  private EmbeddingClient embeddingClient;
  private QdrantClient qdrantClient;
  private KnowledgeChunkMapper chunkMapper;
  private KnowledgeItemMapper itemMapper;
  private KnowledgeBaseItemMapper kbItemMapper;
  private RetrievalService retrievalService;

  @BeforeEach
  void setUp() {
    properties = new RagProperties();
    properties.setTopK(8);
    properties.setScoreThreshold(0.2);
    CurrentOwnerProvider ownerProvider = mock(CurrentOwnerProvider.class);
    when(ownerProvider.getCurrentOwnerId()).thenReturn(OWNER_ID);
    embeddingClient = mock(EmbeddingClient.class);
    when(embeddingClient.embed(any(), any())).thenReturn(List.of(new float[] {1f, 0f}));
    qdrantClient = mock(QdrantClient.class);
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any())).thenReturn(List.of());
    chunkMapper = mock(KnowledgeChunkMapper.class);
    itemMapper = mock(KnowledgeItemMapper.class);
    kbItemMapper = mock(KnowledgeBaseItemMapper.class);
    QdrantProperties qp = new QdrantProperties();
    knowflow.sanjin.modules.embeddingconfig.service.EmbeddingConfigService embeddingConfigService =
        mock(knowflow.sanjin.modules.embeddingconfig.service.EmbeddingConfigService.class);
    when(embeddingConfigService.getCurrentSnapshot())
        .thenReturn(
            new knowflow.sanjin.modules.knowledge.infrastructure.EmbeddingConfigSnapshot(
                "https://example.com/v1", "key", "model", 2));

    retrievalService =
        new RetrievalService(
            properties,
            ownerProvider,
            embeddingClient,
            qdrantClient,
            qp,
            chunkMapper,
            itemMapper,
            kbItemMapper,
            embeddingConfigService);
  }

  private QdrantClient.ScoredPoint scored(
      String chunkId, long itemId, int contentVersion, float score) {
    ObjectNode payload =
        new ObjectNode(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
    payload.put("chunk_id", chunkId);
    payload.put("knowledge_item_id", itemId);
    payload.put("content_version", contentVersion);
    payload.put("user_id", OWNER_ID);
    return new QdrantClient.ScoredPoint("point-" + chunkId, score, payload);
  }

  private KnowledgeChunk chunk(String chunkId, long itemId, int contentVersion) {
    KnowledgeChunk c = new KnowledgeChunk();
    c.setChunkId(chunkId);
    c.setKnowledgeItemId(itemId);
    c.setOwnerId(OWNER_ID);
    c.setContentVersion(contentVersion);
    c.setChunkIndex(0);
    c.setContent("Some detailed content about spring transaction propagation behavior.");
    return c;
  }

  private KnowledgeItem item(long id, Integer indexedVersion, String status) {
    KnowledgeItem i = new KnowledgeItem();
    i.setId(id);
    i.setOwnerId(OWNER_ID);
    i.setIndexedVersion(indexedVersion);
    i.setStatus(status);
    i.setTitle("Item " + id);
    i.setSourceType(KnowledgeConstants.SOURCE_MANUAL_NOTE);
    return i;
  }

  private void stubChunkLookup(KnowledgeChunk... chunks) {
    when(chunkMapper.selectList(any())).thenReturn(java.util.Arrays.asList(chunks));
  }

  private void stubItemLookup(KnowledgeItem... items) {
    when(itemMapper.selectBatchIds(any())).thenReturn(java.util.Arrays.asList(items));
  }

  private void stubActiveKbRelations(Map<Long, Long> itemToKb) {
    List<KnowledgeBaseItem> rels =
        itemToKb.entrySet().stream()
            .map(
                e -> {
                  KnowledgeBaseItem rel = new KnowledgeBaseItem();
                  rel.setKnowledgeItemId(e.getKey());
                  rel.setKnowledgeBaseId(e.getValue());
                  rel.setOwnerId(OWNER_ID);
                  rel.setDeleted(false);
                  return rel;
                })
            .toList();
    when(kbItemMapper.selectList(any())).thenReturn(rels);
  }

  @Test
  @DisplayName("should build owner + OR knowledge-base filter and return valid sources")
  void shouldRetrieveWithOwnerAndKbFilter() {
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any()))
        .thenReturn(List.of(scored("c1", 10L, 1, 0.9f), scored("c2", 11L, 1, 0.8f)));
    stubChunkLookup(chunk("c1", 10L, 1), chunk("c2", 11L, 1));
    stubItemLookup(
        item(10L, 1, KnowledgeConstants.STATUS_ACTIVE),
        item(11L, 1, KnowledgeConstants.STATUS_ACTIVE));
    stubActiveKbRelations(Map.of(10L, 1L, 11L, 1L));

    RetrievalService.RetrievalResult result = retrievalService.retrieve("q", List.of(1L), "q");

    assertThat(result.hasContext()).isTrue();
    assertThat(result.sources()).hasSize(2);
    assertThat(result.trace().getRetrievalQuery()).isEqualTo("q");
    assertThat(result.trace().getQdrantCandidates()).isEqualTo(2);
    assertThat(result.trace().getDiscardedByValidation()).isZero();
  }

  @Test
  @DisplayName("should send Qdrant filter with owner and OR knowledge-base match")
  void shouldFilterByOwnerAndKbOrMatch() {
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any())).thenReturn(List.of());
    stubChunkLookup();
    stubItemLookup();
    stubActiveKbRelations(Map.of());

    retrievalService.retrieve("q", List.of(1L, 2L), "q");

    org.mockito.ArgumentCaptor<Map<String, Object>> filterCaptor =
        org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(qdrantClient).search(any(), any(float[].class), anyInt(), filterCaptor.capture());
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> must =
        (List<Map<String, Object>>) filterCaptor.getValue().get("must");
    assertThat(must).hasSize(2);
    // owner 匹配
    Map<String, Object> ownerClause = must.get(0);
    assertThat(ownerClause.get("key")).isEqualTo("user_id");
    assertThat(((Map<?, ?>) ownerClause.get("match")).get("value")).isEqualTo(OWNER_ID);
    // 多知识库 OR match
    Map<String, Object> kbClause = must.get(1);
    assertThat(kbClause.get("key")).isEqualTo("knowledge_base_ids");
    assertThat(((Map<?, ?>) kbClause.get("match")).get("any")).isEqualTo(List.of(1L, 2L));
  }

  @Test
  @DisplayName("should discard points whose content version is stale vs indexed version")
  void shouldDiscardStaleVersion() {
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any()))
        .thenReturn(List.of(scored("old", 10L, 1, 0.9f), scored("cur", 10L, 2, 0.9f)));
    stubChunkLookup(chunk("old", 10L, 1), chunk("cur", 10L, 2));
    // Item 当前 indexed_version=2 → 仅注入 content_version=2 的 chunk
    stubItemLookup(item(10L, 2, KnowledgeConstants.STATUS_ACTIVE));
    stubActiveKbRelations(Map.of(10L, 1L));

    RetrievalService.RetrievalResult result = retrievalService.retrieve("q", List.of(1L), "q");

    assertThat(result.sources()).hasSize(1);
    assertThat(result.sources().get(0).getSourceId()).isEqualTo("cur");
    assertThat(result.trace().getDiscardedByValidation()).isEqualTo(1);
  }

  @Test
  @DisplayName("should discard chunks whose item is not active or is deleted")
  void shouldDiscardDeletedItem() {
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any()))
        .thenReturn(List.of(scored("d", 10L, 1, 0.9f)));
    stubChunkLookup(chunk("d", 10L, 1));
    stubItemLookup(item(10L, 1, KnowledgeConstants.STATUS_DELETED));
    stubActiveKbRelations(Map.of(10L, 1L));

    RetrievalService.RetrievalResult result = retrievalService.retrieve("q", List.of(1L), "q");

    assertThat(result.hasContext()).isFalse();
    assertThat(result.trace().getDiscardedByValidation()).isEqualTo(1);
  }

  @Test
  @DisplayName("should discard chunks whose KB relation was removed")
  void shouldDiscardWhenRelationRemoved() {
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any()))
        .thenReturn(List.of(scored("r", 10L, 1, 0.9f)));
    stubChunkLookup(chunk("r", 10L, 1));
    stubItemLookup(item(10L, 1, KnowledgeConstants.STATUS_ACTIVE));
    // Item 仅归属 KB 2，而 Router 选中 KB 1 → 无交集，剔除
    stubActiveKbRelations(Map.of(10L, 2L));

    RetrievalService.RetrievalResult result = retrievalService.retrieve("q", List.of(1L), "q");

    assertThat(result.hasContext()).isFalse();
    assertThat(result.trace().getDiscardedByValidation()).isEqualTo(1);
  }

  @Test
  @DisplayName("should discard low-score candidates below the threshold")
  void shouldDiscardBelowThreshold() {
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any()))
        .thenReturn(List.of(scored("low", 10L, 1, 0.05f)));
    stubChunkLookup(chunk("low", 10L, 1));
    stubItemLookup(item(10L, 1, KnowledgeConstants.STATUS_ACTIVE));
    stubActiveKbRelations(Map.of(10L, 1L));

    RetrievalService.RetrievalResult result = retrievalService.retrieve("q", List.of(1L), "q");

    assertThat(result.hasContext()).isFalse();
    assertThat(result.trace().getDiscardedByValidation()).isEqualTo(1);
  }

  @Test
  @DisplayName("should return empty when qdrant returns nothing")
  void shouldReturnEmptyWhenNoCandidates() {
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any())).thenReturn(List.of());

    RetrievalService.RetrievalResult result = retrievalService.retrieve("q", List.of(1L), "q");

    assertThat(result.hasContext()).isFalse();
    assertThat(result.trace().getQdrantCandidates()).isZero();
  }

  @Test
  @DisplayName("should fall back to the question when retrievalQuery is blank")
  void shouldFallBackToQuestionWhenQueryBlank() {
    RetrievalService.RetrievalResult result =
        retrievalService.retrieve("  ", List.of(1L), "current question");

    assertThat(result.trace().getRetrievalQuery()).isEqualTo("current question");
  }

  @Test
  @DisplayName("should propagate embedding failure for the caller to degrade")
  void shouldPropagateEmbeddingFailure() {
    when(embeddingClient.embed(any(), any()))
        .thenThrow(
            new knowflow.sanjin.modules.knowledge.exception.RetryableIndexException(
                knowflow.sanjin.common.error.ErrorCode.EMBEDDING_UNAVAILABLE, "down", null));

    assertThatThrownBy(() -> retrievalService.retrieve("q", List.of(1L), "q"))
        .isInstanceOf(knowflow.sanjin.modules.knowledge.exception.RetryableIndexException.class);
  }

  @Test
  @DisplayName("should not include retrieved sources that are not cited and keep score ordering")
  void shouldOrderByScoreDesc() {
    when(qdrantClient.search(any(), any(float[].class), anyInt(), any()))
        .thenReturn(List.of(scored("cA", 10L, 1, 0.5f), scored("cB", 11L, 1, 0.9f)));
    stubChunkLookup(chunk("cA", 10L, 1), chunk("cB", 11L, 1));
    stubItemLookup(
        item(10L, 1, KnowledgeConstants.STATUS_ACTIVE),
        item(11L, 1, KnowledgeConstants.STATUS_ACTIVE));
    stubActiveKbRelations(Map.of(10L, 1L, 11L, 1L));

    RetrievalService.RetrievalResult result = retrievalService.retrieve("q", List.of(1L), "q");

    assertThat(result.sources().get(0).getSourceId()).isEqualTo("cB");
    assertThat(result.sources().get(1).getSourceId()).isEqualTo("cA");
    List<RetrievedSource> sources = result.sources();
    assertThat(sources).allMatch(s -> !s.isCited()); // cited 由生成后解析确定，检索阶段不标记
    assertThat(sources.get(0).getSnippet()).isNotBlank();
  }
}
