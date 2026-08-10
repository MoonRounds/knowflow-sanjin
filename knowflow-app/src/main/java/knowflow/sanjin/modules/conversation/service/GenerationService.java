package knowflow.sanjin.modules.conversation.service;

import java.util.ArrayList;
import java.util.List;
import knowflow.sanjin.modules.conversation.dto.RegenerateRequest;
import knowflow.sanjin.modules.conversation.dto.SendMessageRequest;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.memory.MemoryService;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.rag.dto.RagContext;
import knowflow.sanjin.modules.rag.service.RagContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Generation Orchestrator：编排发送/重新生成的持久化（Tx1）、模型客户端创建、上下文组装与流式执行。
 *
 * <p>事务边界：Tx1 由 {@link GenerationPrepare} 在事务内完成（行锁 conversation + 认领 active slot +
 * 创建消息），提交后返回；流式在独立 executor 线程执行，不持有数据库事务；最终状态与 slot 释放由 {@link GenerationStreamer} 的 finalizer 在
 * Tx2 写入。
 *
 * <p>模型客户端创建与上下文组装在 executor 任务内执行（而非请求线程）：任何 pre-stream 异常（模型配置被禁用、解密失败等）都会经 streamer 的失败路径 {@link
 * GenerationFinalizer#fail} 释放 active slot，避免 slot 永久卡死。
 */
@Service
public class GenerationService {

  private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

  private final GenerationPrepare prepare;
  private final ConversationService conversationService;
  private final ModelClientFactory modelClientFactory;
  private final GenerationExecutor executor;
  private final GenerationStreamer streamer;
  private final SseEmitterFactory emitterFactory;
  private final CurrentOwnerProvider currentOwnerProvider;
  private final MemoryService memoryService;
  private final RagContextBuilder ragContextBuilder;

  public GenerationService(
      GenerationPrepare prepare,
      ConversationService conversationService,
      ModelClientFactory modelClientFactory,
      GenerationExecutor executor,
      GenerationStreamer streamer,
      SseEmitterFactory emitterFactory,
      CurrentOwnerProvider currentOwnerProvider,
      MemoryService memoryService,
      RagContextBuilder ragContextBuilder) {
    this.prepare = prepare;
    this.conversationService = conversationService;
    this.modelClientFactory = modelClientFactory;
    this.executor = executor;
    this.streamer = streamer;
    this.emitterFactory = emitterFactory;
    this.currentOwnerProvider = currentOwnerProvider;
    this.memoryService = memoryService;
    this.ragContextBuilder = ragContextBuilder;
  }

  /** 发送一条 User 消息并流式生成回答。返回 SseEmitter，客户端消费事件流。 */
  public SseEmitter send(Long conversationId, SendMessageRequest request) {
    GenerationPrepare.PreparedSend prepared;
    try {
      prepared = prepare.prepareSend(conversationId, request);
    } catch (DuplicateKeyException e) {
      // clientMessageId 幂等：重复请求不创建新流，返回空流，客户端对账最终状态
      log.info(
          "Duplicate clientMessageId {} for conversation {}",
          request.getClientMessageId(),
          conversationId);
      return emitterFactory.createEmpty();
    }
    return dispatchStream(conversationId, prepared);
  }

  /** 重新生成：在最新 assistant 消息上创建新 attempt。 */
  public SseEmitter regenerate(Long conversationId, RegenerateRequest request) {
    GenerationPrepare.PreparedSend prepared =
        prepare.prepareRegenerate(
            conversationId, request != null ? request.getModelConfigId() : null);
    return dispatchStream(conversationId, prepared);
  }

  /** 停止当前 active generation。 */
  public void stop(Long conversationId) {
    Conversation c = conversationService.lockConversation(conversationId);
    if (c.getActiveGenerationMessageId() != null) {
      executor.cancel(c.getActiveGenerationMessageId());
    }
  }

  // ---- dispatch ----

  private SseEmitter dispatchStream(Long conversationId, GenerationPrepare.PreparedSend prepared) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    long assistantMessageId = prepared.assistantMessage().getId();
    // 当前用户消息：发送时为新建 user 消息；重生成时为原 attempt 回复的 user 消息
    Long currentUserMessageId =
        prepared.userMessage() != null
            ? prepared.userMessage().getId()
            : prepared.assistantMessage().getReplyToMessageId();

    SseEmitter emitter = emitterFactory.create();
    executor.submit(
        assistantMessageId,
        () -> {
          try {
            // 模型创建与上下文组装移入任务内：pre-stream 异常由 streamer 失败路径释放 slot
            ChatModel chatModel = modelClientFactory.create(prepared.revision());
            ChatMessage current =
                conversationService.getMessage(conversationId, currentUserMessageId);
            // RAG 上下文在流式前同步构造；失败只降级（普通生成），不终止 Generation
            RagContext ragContext = ragContextBuilder.build(conversationId, current.getContent());
            List<Message> promptMessages =
                buildPrompt(conversationId, current.getContent(), ragContext);
            GenerationContext ctx =
                new GenerationContext(
                    conversationId,
                    ownerId,
                    assistantMessageId,
                    prepared.revision(),
                    chatModel,
                    promptMessages,
                    ragContext);
            streamer.stream(ctx, emitter);
          } catch (java.io.IOException e) {
            // 断连等 IOException 由 streamer 内部统一终结并释放 slot
            log.warn("Streaming aborted for generation {}: {}", assistantMessageId, e.getMessage());
          }
        });
    return emitter;
  }

  private List<Message> buildPrompt(
      long conversationId, String currentQuestion, RagContext ragContext) {
    List<Message> result = new ArrayList<>(memoryService.loadWindow(conversationId));
    // RAG 材料作为独立 UserMessage 注入，标注不可信引用，与系统指令分离
    if (ragContext != null
        && ragContext.getInjectedText() != null
        && !ragContext.getInjectedText().isBlank()) {
      result.add(new UserMessage(ragContext.getInjectedText()));
    }
    result.add(new UserMessage(currentQuestion));
    return result;
  }
}
