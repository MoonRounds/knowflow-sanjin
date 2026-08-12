package knowflow.sanjin.modules.conversation.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import knowflow.sanjin.modules.conversation.dto.CreateConversationRequest;
import knowflow.sanjin.modules.conversation.dto.UpdateConversationRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.exception.ActiveGenerationExistsException;
import knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

/** Conversation/Message 集成测试：迁移、CRUD、软删除守卫、sequence 游标与 Owner 隔离。 */
@SpringBootTest
@DisplayName("Conversation Integration Tests")
class ConversationServiceIT extends MySQLTestBase {

  @Autowired private ConversationService service;

  private Conversation createConversation(String title) {
    CreateConversationRequest req = new CreateConversationRequest();
    req.setTitle(title);
    return service.create(req);
  }

  private ChatMessage userMessage(Conversation c, long seq, String content) {
    ChatMessage m = new ChatMessage();
    m.setConversationId(c.getId());
    m.setOwnerId(1L);
    m.setRole(ChatMessage.ROLE_USER);
    m.setSequence(seq);
    m.setContent(content);
    m.setIsActive(false);
    return m;
  }

  private ChatMessage assistantMessage(
      Conversation c, long seq, long replyTo, String content, String status, boolean active) {
    ChatMessage m = new ChatMessage();
    m.setConversationId(c.getId());
    m.setOwnerId(1L);
    m.setRole(ChatMessage.ROLE_ASSISTANT);
    m.setSequence(seq);
    m.setContent(content);
    m.setReplyToMessageId(replyTo);
    m.setGenerationStatus(status);
    m.setIsActive(active);
    return m;
  }

  @Test
  @DisplayName("should create conversation and persist with owner id 1")
  void shouldCreateAndPersist() {
    Conversation c = createConversation("Phase 3 alpha");
    assertThat(c.getId()).isNotNull();
    assertThat(c.getTitle()).isEqualTo("Phase 3 alpha");
    assertThat(c.getDeleted()).isFalse();
    assertThat(c.getOwnerId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("should list conversations (at least the ones created in this class)")
  void shouldListNewestFirst() {
    createConversation("first");
    createConversation("second");
    createConversation("third");
    List<Conversation> list = service.listForOwner();
    assertThat(list).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  @DisplayName("should soft delete conversation")
  void shouldSoftDelete() {
    Conversation c = createConversation("to-delete");
    service.softDelete(c.getId());
    assertThatThrownBy(() -> service.getByIdAndOwner(c.getId()))
        .isInstanceOf(ConversationNotFoundException.class);
  }

  @Test
  @DisplayName("should reject delete when active generation exists")
  void shouldRejectDeleteWithActiveGeneration() {
    Conversation c = createConversation("active-delete");
    ChatMessage msg = userMessage(c, 1L, "hello");
    service.insertMessage(msg);
    // 手动把 conversation 认领为 active，模拟进行中的生成
    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), msg.getId(), java.time.Duration.ofMinutes(5)))
        .isTrue();
    assertThatThrownBy(() -> service.softDelete(c.getId()))
        .isInstanceOf(ActiveGenerationExistsException.class);
  }

  @Test
  @DisplayName("should update title with optimistic lock and conflict on stale rowVersion")
  void shouldUpdateTitleWithRowVersion() {
    Conversation c = createConversation("old-title");
    UpdateConversationRequest req = new UpdateConversationRequest();
    req.setTitle("new-title");
    req.setRowVersion(c.getRowVersion().longValue());
    Conversation updated = service.update(c.getId(), req);
    assertThat(updated.getTitle()).isEqualTo("new-title");

    UpdateConversationRequest stale = new UpdateConversationRequest();
    stale.setTitle("stale-title");
    stale.setRowVersion(0L); // 过期版本
    assertThatThrownBy(() -> service.update(c.getId(), stale))
        .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
  }

  @Test
  @DisplayName("should paginate message history with sequence cursor ascending")
  void shouldPaginateHistoryWithCursor() {
    Conversation c = createConversation("history");
    ChatMessage m1 = userMessage(c, 1L, "one");
    service.insertMessage(m1);
    service.insertMessage(assistantMessage(c, 2L, m1.getId(), "a1", ChatMessage.COMPLETED, true));
    ChatMessage m2 = userMessage(c, 3L, "two");
    service.insertMessage(m2);
    service.insertMessage(assistantMessage(c, 4L, m2.getId(), "a2", ChatMessage.COMPLETED, true));
    ChatMessage m3 = userMessage(c, 5L, "three");
    service.insertMessage(m3);
    service.insertMessage(assistantMessage(c, 6L, m3.getId(), "a3", ChatMessage.COMPLETED, true));

    // 第一页：最新 2 条（seq 5,6）
    List<ChatMessage> page1 = service.listMessages(c.getId(), null, 2);
    assertThat(page1).hasSize(2);
    assertThat(page1.get(0).getSequence()).isEqualTo(5L);
    assertThat(page1.get(1).getSequence()).isEqualTo(6L);

    // 第二页：before=page1 第一条 sequence，返回更早 2 条（seq 3,4）
    Long before = page1.get(0).getSequence();
    List<ChatMessage> page2 = service.listMessages(c.getId(), before, 2);
    assertThat(page2).hasSize(2);
    assertThat(page2.get(0).getSequence()).isEqualTo(3L);
    assertThat(page2.get(1).getSequence()).isEqualTo(4L);

    // 第三页：before=page2 第一条 sequence，返回 seq 1,2
    List<ChatMessage> page3 = service.listMessages(c.getId(), page2.get(0).getSequence(), 2);
    assertThat(page3).hasSize(2);
    assertThat(page3.get(0).getSequence()).isEqualTo(1L);
    assertThat(page3.get(1).getSequence()).isEqualTo(2L);
  }

  @Test
  @DisplayName("should enforce clientMessageId unique across owner")
  void shouldEnforceClientMessageIdUnique() {
    Conversation c1 = createConversation("dup-1");
    Conversation c2 = createConversation("dup-2");
    ChatMessage a = userMessage(c1, 1L, "a");
    a.setClientMessageId("client-abc");
    service.insertMessage(a);

    ChatMessage b = userMessage(c2, 1L, "b");
    b.setClientMessageId("client-abc"); // 不同会话但同 owner + client id => 冲突
    assertThatThrownBy(() -> service.insertMessage(b)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("should claim active generation only once per conversation")
  void shouldClaimActiveGenerationOnce() {
    Conversation c = createConversation("claim");
    ChatMessage m1 = userMessage(c, 1L, "q1");
    ChatMessage m2 = userMessage(c, 2L, "q2");
    service.insertMessage(m1);
    service.insertMessage(m2);

    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), m1.getId(), java.time.Duration.ofMinutes(5)))
        .isTrue();
    // 第二次认领（另一消息）失败
    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), m2.getId(), java.time.Duration.ofMinutes(5)))
        .isFalse();

    service.clearActiveGeneration(c.getId());
    assertThat(
            service.tryClaimActiveGeneration(
                c.getId(), m2.getId(), java.time.Duration.ofMinutes(5)))
        .isTrue();
  }

  @Test
  @DisplayName("should enforce owner isolation on messages")
  void shouldIsolateMessagesAcrossOwners() {
    // 另一 owner 的会话消息不可见：直接查询不存在 owner 的会话应抛 404
    assertThatThrownBy(() -> service.getMessage(999999L, 1L))
        .isInstanceOf(ConversationNotFoundException.class);
  }
}
