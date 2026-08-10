package knowflow.sanjin.modules.conversation.service;

import java.util.List;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.rag.dto.RagContext;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 一次 Generation 的运行时上下文：消息持久化完成后在事务外传递给流式执行器。
 *
 * <p>对象在请求线程创建、提交事务后移交到生成执行线程，最终在 finalizer 中释放 active slot。
 */
public final class GenerationContext {

  private final long conversationId;
  private final long ownerId;
  private final long assistantMessageId;
  private final ModelConfigRevision revision;
  private final ChatModel chatModel;
  private final List<Message> promptMessages;
  private final RagContext ragContext;

  public GenerationContext(
      long conversationId,
      long ownerId,
      long assistantMessageId,
      ModelConfigRevision revision,
      ChatModel chatModel,
      List<Message> promptMessages,
      RagContext ragContext) {
    this.conversationId = conversationId;
    this.ownerId = ownerId;
    this.assistantMessageId = assistantMessageId;
    this.revision = revision;
    this.chatModel = chatModel;
    this.promptMessages = promptMessages;
    this.ragContext = ragContext;
  }

  public long conversationId() {
    return conversationId;
  }

  public long ownerId() {
    return ownerId;
  }

  public long assistantMessageId() {
    return assistantMessageId;
  }

  public ModelConfigRevision revision() {
    return revision;
  }

  public ChatModel chatModel() {
    return chatModel;
  }

  public List<Message> promptMessages() {
    return promptMessages;
  }

  public RagContext ragContext() {
    return ragContext;
  }
}
