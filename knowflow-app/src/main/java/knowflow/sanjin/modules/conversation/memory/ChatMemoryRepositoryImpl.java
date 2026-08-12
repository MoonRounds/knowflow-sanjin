package knowflow.sanjin.modules.conversation.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 轻量 Spring AI {@link ChatMemoryRepository} 适配器：基于 {@link MemoryStore} 的投影读写。
 *
 * <p>key 格式为 {@code <keyPrefix>:<ownerId>:chat:<conversationId>}，ownerId + conversationId 共同构成
 * 隔离边界（Conversation 属 owner）。TTL 在每次保存/覆盖时刷新，按不活跃时间过期；不实现任何高级查询。
 */
public class ChatMemoryRepositoryImpl implements ChatMemoryRepository {

  public static final String CONVERSATION_PREFIX = "chat:";

  private final MemoryStore store;
  private final MemoryProperties properties;
  private final CurrentOwnerProvider ownerProvider;

  public ChatMemoryRepositoryImpl(
      MemoryStore store, MemoryProperties properties, CurrentOwnerProvider ownerProvider) {
    this.store = store;
    this.properties = properties;
    this.ownerProvider = ownerProvider;
  }

  @Override
  public List<String> findConversationIds() {
    String prefix = conversationPrefix();
    Set<String> keys = store.keys(prefix);
    List<String> ids = new ArrayList<>();
    for (String key : keys) {
      String id = key.substring(prefix.length());
      ids.add(id);
    }
    return ids;
  }

  @Override
  public List<Message> findByConversationId(String conversationId) {
    return store.get(key(conversationId)).map(this::toMessages).orElseGet(ArrayList::new);
  }

  @Override
  public void saveAll(String conversationId, List<Message> messages) {
    MemorySnapshot snapshot = new MemorySnapshot();
    snapshot.setSchemaVersion(properties.getSchemaVersion());
    snapshot.setMessages(toTurns(messages));
    store.save(key(conversationId), snapshot, properties.getTtl());
  }

  @Override
  public void deleteByConversationId(String conversationId) {
    store.delete(key(conversationId));
  }

  /** {@code <keyPrefix>:<ownerId>:chat:} 前缀，用于扫描当前 Owner 的全部会话投影。 */
  private String conversationPrefix() {
    return properties.getKeyPrefix()
        + ":"
        + ownerProvider.getCurrentOwnerId()
        + ":"
        + CONVERSATION_PREFIX;
  }

  private String key(String conversationId) {
    return conversationPrefix() + conversationId;
  }

  private List<MemoryTurn> toTurns(List<Message> messages) {
    List<MemoryTurn> turns = new ArrayList<>(messages.size());
    for (Message m : messages) {
      if (m instanceof UserMessage) {
        turns.add(new MemoryTurn(MemoryTurn.ROLE_USER, m.getText()));
      } else if (m instanceof AssistantMessage) {
        turns.add(new MemoryTurn(MemoryTurn.ROLE_ASSISTANT, m.getText()));
      }
    }
    return turns;
  }

  private List<Message> toMessages(MemorySnapshot snapshot) {
    List<Message> messages = new ArrayList<>();
    if (snapshot.getMessages() == null) {
      return messages;
    }
    for (MemoryTurn turn : snapshot.getMessages()) {
      if (MemoryTurn.ROLE_USER.equals(turn.getRole())) {
        messages.add(new UserMessage(turn.getContent()));
      } else if (MemoryTurn.ROLE_ASSISTANT.equals(turn.getRole())) {
        messages.add(new AssistantMessage(turn.getContent()));
      }
    }
    return messages;
  }
}
