package knowflow.sanjin.modules.conversation.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/** ChatMemoryRepositoryImpl 单元测试：角色/内容往返、key 隔离、TTL 传递、findConversationIds。 */
class ChatMemoryRepositoryImplTest {

  private MemoryProperties properties;
  private InMemoryMemoryStore store;

  @BeforeEach
  void setUp() {
    properties = new MemoryProperties();
    store = new InMemoryMemoryStore();
  }

  @Test
  void savesAndLoadsMessagesWithRoleContentRoundTrip() {
    ChatMemoryRepositoryImpl repo =
        new ChatMemoryRepositoryImpl(store, properties, new CurrentOwnerProvider());
    repo.saveAll("42", List.of(new UserMessage("u1"), new AssistantMessage("a1")));

    List<Message> loaded = repo.findByConversationId("42");
    assertThat(loaded).hasSize(2);
    assertThat(loaded.get(0)).isInstanceOf(UserMessage.class);
    assertThat(loaded.get(0).getText()).isEqualTo("u1");
    assertThat(loaded.get(1)).isInstanceOf(AssistantMessage.class);
    assertThat(loaded.get(1).getText()).isEqualTo("a1");
  }

  @Test
  void isolatesKeysByConversationId() {
    ChatMemoryRepositoryImpl repo =
        new ChatMemoryRepositoryImpl(store, properties, new CurrentOwnerProvider());
    repo.saveAll("1", List.of(new UserMessage("u1")));
    repo.saveAll("2", List.of(new UserMessage("u2")));

    assertThat(repo.findByConversationId("1")).hasSize(1);
    assertThat(repo.findByConversationId("1").get(0).getText()).isEqualTo("u1");
    assertThat(repo.findByConversationId("2").get(0).getText()).isEqualTo("u2");
  }

  @Test
  void usesVersionedKeyPrefixAndPassesTtl() {
    MemoryStore mockStore = mock(MemoryStore.class);
    ChatMemoryRepositoryImpl repo =
        new ChatMemoryRepositoryImpl(mockStore, properties, new CurrentOwnerProvider());
    repo.saveAll("7", List.of(new UserMessage("u")));

    verify(mockStore).save(eq("knowflow:chat-memory:v1:1:chat:7"), any(), eq(Duration.ofDays(7)));
  }

  @Test
  void findConversationIdsReturnsConversationIds() {
    ChatMemoryRepositoryImpl repo =
        new ChatMemoryRepositoryImpl(store, properties, new CurrentOwnerProvider());
    repo.saveAll("11", List.of(new UserMessage("u")));
    repo.saveAll("12", List.of(new UserMessage("u")));

    assertThat(repo.findConversationIds()).containsExactlyInAnyOrder("11", "12");
  }

  @Test
  void deleteRemovesByConversationId() {
    ChatMemoryRepositoryImpl repo =
        new ChatMemoryRepositoryImpl(store, properties, new CurrentOwnerProvider());
    repo.saveAll("5", List.of(new UserMessage("u")));
    repo.deleteByConversationId("5");

    assertThat(repo.findByConversationId("5")).isEmpty();
  }

  @Test
  void findMissingConversationReturnsEmpty() {
    ChatMemoryRepositoryImpl repo =
        new ChatMemoryRepositoryImpl(store, properties, new CurrentOwnerProvider());
    assertThat(repo.findByConversationId("nope")).isEmpty();
  }
}
