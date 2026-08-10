package knowflow.sanjin.modules.conversation.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.dao.DataAccessResourceFailureException;

/** MemoryService 单元测试：Redis 命中/miss/故障降级、写失败容错、空窗口清理。 */
class MemoryServiceTest {

  private ChatMemoryRepositoryImpl repository;
  private ConversationService conversationService;
  private MemoryService service;
  private MemoryProperties properties;

  @BeforeEach
  void setUp() {
    properties = new MemoryProperties();
    properties.setTurns(10);
    InMemoryMemoryStore store = new InMemoryMemoryStore();
    repository = new ChatMemoryRepositoryImpl(store, properties);
    conversationService = mock(ConversationService.class);
    service = new MemoryService(repository, conversationService, properties);
  }

  @Test
  void returnsFromRedisWhenPresent() {
    repository.saveAll("1", List.of(new UserMessage("from-redis"), new AssistantMessage("a")));

    List<Message> window = service.loadWindow(1L);
    assertThat(window).hasSize(2);
    assertThat(window.get(0).getText()).isEqualTo("from-redis");
    verify(conversationService, never()).loadRecentContext(anyLong(), anyInt());
  }

  @Test
  void rebuildsFromMysqlOnRedisMiss() {
    ChatMessage user = message(ChatMessage.ROLE_USER, "u1", 1L);
    ChatMessage assistant = message(ChatMessage.ROLE_ASSISTANT, "a1", 2L);
    when(conversationService.loadRecentContext(1L, 10)).thenReturn(List.of(user, assistant));

    List<Message> window = service.loadWindow(1L);
    assertThat(window).hasSize(2);
    assertThat(window.get(0).getText()).isEqualTo("u1");
    assertThat(window.get(1).getText()).isEqualTo("a1");
    // 重建后写回投影
    assertThat(repository.findByConversationId("1")).hasSize(2);
  }

  @Test
  void fallsBackToMysqlWhenRedisReadFails() {
    ChatMessage user = message(ChatMessage.ROLE_USER, "u-fallback", 1L);
    when(conversationService.loadRecentContext(1L, 10)).thenReturn(List.of(user));
    ChatMemoryRepositoryImpl failing = failingRepository();

    MemoryService svc = new MemoryService(failing, conversationService, properties);
    List<Message> window = svc.loadWindow(1L);
    assertThat(window).hasSize(1);
    assertThat(window.get(0).getText()).isEqualTo("u-fallback");
  }

  @Test
  void writeFailureIsTolerated() {
    ChatMessage user = message(ChatMessage.ROLE_USER, "u1", 1L);
    when(conversationService.loadRecentContext(1L, 10)).thenReturn(List.of(user));
    ChatMemoryRepositoryImpl failing = failingRepository();

    MemoryService svc = new MemoryService(failing, conversationService, properties);
    // 重建时写 Redis 失败，但返回 MySQL 数据（不抛错）
    List<Message> window = svc.loadWindow(1L);
    assertThat(window).hasSize(1);
    assertThat(window.get(0).getText()).isEqualTo("u1");
  }

  @Test
  void clearIsToleratedOnRedisFailure() {
    ChatMemoryRepositoryImpl failing = failingRepository();
    MemoryService svc = new MemoryService(failing, conversationService, properties);
    // 删除失败不应抛错
    svc.clear(1L);
  }

  @Test
  void emptyWindowClearsProjection() {
    when(conversationService.loadRecentContext(1L, 10)).thenReturn(List.of());
    repository.saveAll("1", List.of(new UserMessage("stale")));

    List<Message> window = service.rebuild(1L);
    assertThat(window).isEmpty();
    assertThat(repository.findByConversationId("1")).isEmpty();
  }

  private ChatMemoryRepositoryImpl failingRepository() {
    ChatMemoryRepositoryImpl failing = mock(ChatMemoryRepositoryImpl.class);
    doThrow(new DataAccessResourceFailureException("redis down"))
        .when(failing)
        .findByConversationId(anyString());
    doThrow(new DataAccessResourceFailureException("redis down"))
        .when(failing)
        .saveAll(anyString(), any());
    doThrow(new DataAccessResourceFailureException("redis down"))
        .when(failing)
        .deleteByConversationId(anyString());
    return failing;
  }

  private static ChatMessage message(String role, String content, long id) {
    ChatMessage m = new ChatMessage();
    m.setId(id);
    m.setRole(role);
    m.setContent(content);
    return m;
  }
}
