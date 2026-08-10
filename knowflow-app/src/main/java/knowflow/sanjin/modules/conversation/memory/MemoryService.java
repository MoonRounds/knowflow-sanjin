package knowflow.sanjin.modules.conversation.memory;

import java.util.List;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

/**
 * Chat Memory 投影门面：读优先 Redis，miss/故障时从 MySQL 重建并回写；写失败不影响 MySQL 已提交结果。
 *
 * <p>读容错：Redis miss、过期、反序列化失败或连接异常都回源 {@link ConversationService#loadRecentContext} 重建， 任何 Redis
 * 异常都不让请求失败。写容错：重建/刷新写 Redis 失败只记日志（投影可丢失，MySQL 是事实源）。
 */
@Service
public class MemoryService {

  private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

  private final ChatMemoryRepositoryImpl repository;
  private final ConversationService conversationService;
  private final MemoryProperties properties;

  public MemoryService(
      ChatMemoryRepositoryImpl repository,
      ConversationService conversationService,
      MemoryProperties properties) {
    this.repository = repository;
    this.conversationService = conversationService;
    this.properties = properties;
  }

  /** 读取最近完整 active Turn 上下文；Redis miss/故障时从 MySQL 重建并回写。 */
  public List<Message> loadWindow(Long conversationId) {
    try {
      List<Message> fromRedis = repository.findByConversationId(conversationId.toString());
      if (!fromRedis.isEmpty()) {
        return fromRedis;
      }
    } catch (RuntimeException e) {
      log.warn(
          "Redis memory read failed for conversation {}, rebuilding from MySQL: {}",
          conversationId,
          e.getMessage());
    }
    return rebuild(conversationId);
  }

  /** 从 MySQL 构造最近完整 active Turn 窗口，并写回 Redis 投影。 */
  public List<Message> rebuild(Long conversationId) {
    List<ChatMessage> history;
    try {
      history = conversationService.loadRecentContext(conversationId, properties.getTurns());
    } catch (knowflow.sanjin.modules.conversation.exception.ConversationNotFoundException e) {
      // 会话已删除：清除残留投影，不重建已删会话的 Memory
      clear(conversationId);
      return List.of();
    }
    List<Message> messages = toMessages(history);
    if (messages.isEmpty()) {
      clear(conversationId);
      return messages;
    }
    try {
      repository.saveAll(conversationId.toString(), messages);
    } catch (RuntimeException e) {
      log.warn(
          "Redis memory write failed for conversation {} (ignored): {}",
          conversationId,
          e.getMessage());
    }
    return messages;
  }

  /** 删除投影（Conversation 删除后调用）；失败仅记日志。 */
  public void clear(Long conversationId) {
    try {
      repository.deleteByConversationId(conversationId.toString());
    } catch (RuntimeException e) {
      log.warn(
          "Redis memory clear failed for conversation {} (ignored): {}",
          conversationId,
          e.getMessage());
    }
  }

  private List<Message> toMessages(List<ChatMessage> history) {
    return history.stream()
        .map(
            m -> {
              if (ChatMessage.ROLE_USER.equals(m.getRole())) {
                return (Message) new UserMessage(m.getContent());
              }
              return (Message) new AssistantMessage(m.getContent());
            })
        .toList();
  }
}
