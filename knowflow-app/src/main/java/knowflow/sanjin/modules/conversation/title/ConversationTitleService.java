package knowflow.sanjin.modules.conversation.title;

import java.util.List;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 会话标题 AI 自动生成（ADR 0004 / DECISIONS §8）。
 *
 * <p>{@code ensureTitleTask} 在首轮回答结束后的生成线程触发：仅当会话标题仍为「新对话」占位时提交 ProcessingTask（靠 {@code (task_type,
 * business_key, active_flag)} 活跃唯一约束防重复提交），提交在 事务内完成并 {@code publishAfterCommit}；任何异常只记日志，不影响生成主链路。
 *
 * <p>{@code execute} 由 Consumer 在标题工作队列上调用：幂等守卫（标题已非占位则跳过）→ 加载首轮完整对话 → 显式用 Utility Model 生成一句话中文标题
 * → 校验/截断 → 原子写入。任何失败（Utility 未配置/被禁用/超时/ 调用异常）都静默回退为「首条 User Message 安全截断」，不抛业务异常。
 */
@Service
public class ConversationTitleService {

  private static final Logger log = LoggerFactory.getLogger(ConversationTitleService.class);

  /** 任务最大重试次数（Consumer 可重试档位 3 次）。 */
  private static final int MAX_RETRIES = 2;

  /** AI 生成标题的最长长度（存储层上限为 200，这里按需求取宽松值）。 */
  private static final int MAX_TITLE_LENGTH = 40;

  /** 失败回退：首条 User Message 截断长度。 */
  private static final int FALLBACK_TITLE_LENGTH = 30;

  /** 会话标题工作队列基名（与 RabbitProperties.conversationTitleWorkQueue 一致）。 */
  public static final String WORK_QUEUE_BASE = "conversation-title.work";

  private final ConversationService conversationService;
  private final TaskSubmissionService taskSubmissionService;
  private final ModelConfigService modelConfigService;
  private final ModelClientFactory modelClientFactory;
  private final CurrentOwnerProvider currentOwnerProvider;
  private final ObjectMapper objectMapper;

  public ConversationTitleService(
      ConversationService conversationService,
      TaskSubmissionService taskSubmissionService,
      ModelConfigService modelConfigService,
      ModelClientFactory modelClientFactory,
      CurrentOwnerProvider currentOwnerProvider,
      ObjectMapper objectMapper) {
    this.conversationService = conversationService;
    this.taskSubmissionService = taskSubmissionService;
    this.modelConfigService = modelConfigService;
    this.modelClientFactory = modelClientFactory;
    this.currentOwnerProvider = currentOwnerProvider;
    this.objectMapper = objectMapper;
  }

  /** 首轮回答结束后触发：仅当标题仍为占位时提交标题生成任务。任何异常只记日志，不影响生成主链路。 */
  public void ensureTitleTask(long conversationId) {
    try {
      Conversation conversation = conversationService.getByIdAndOwner(conversationId);
      if (conversation == null
          || !ConversationService.TITLE_PLACEHOLDER.equals(conversation.getTitle())) {
        // 已手动改名或标题已生成：无需提交
        return;
      }
      String payload = objectMapper.writeValueAsString(new TitleTaskPayload(conversationId));
      // businessKey = conversationId 字符串；活跃唯一约束 (task_type, business_key, active_flag) 防重复提交
      taskSubmissionService.submit(
          ProcessingConstants.TASK_TYPE_CONVERSATION_TITLE,
          String.valueOf(conversationId),
          conversationId,
          currentOwnerProvider.getCurrentOwnerId(),
          payload,
          MAX_RETRIES,
          WORK_QUEUE_BASE);
      log.info("已提交会话标题生成任务 conversation={}", conversationId);
    } catch (Exception e) {
      // 标题生成是旁路能力：任何失败都不影响生成主链路，只记日志
      log.warn("提交会话标题生成任务失败 conversation={}", conversationId, e);
    }
  }

  /** Consumer 入口：幂等生成标题。内部已做失败回退，正常不抛业务异常。 */
  public void execute(ProcessingTask task) {
    try {
      TitleTaskPayload payload = objectMapper.readValue(task.getPayload(), TitleTaskPayload.class);
      long conversationId = payload.conversationId();

      // 幂等守卫：标题已非占位（AI 已生成或用户手动改名）直接返回成功，不覆盖
      Conversation conversation = conversationService.getByIdAndOwner(conversationId);
      if (conversation == null
          || !ConversationService.TITLE_PLACEHOLDER.equals(conversation.getTitle())) {
        log.info("会话标题已非占位，跳过标题生成 conversation={}", conversationId);
        return;
      }

      List<ChatMessage> firstTurn = loadFirstTurn(conversationId);
      String generated = generateTitle(conversationId, firstTurn);
      if (generated == null) {
        // Utility 不可用/失败/超时 → 回退首条消息截断
        generated = fallbackTitle(firstTurn);
        log.info("会话标题生成失败，回退首条消息截断 conversation={}", conversationId);
      }
      writeTitleIfStillPlaceholder(conversationId, generated);
    } catch (Exception e) {
      // 兜底：任何未知异常都按首条消息截断回退，避免任务进入失败重试
      log.warn("会话标题生成异常，回退首条消息截断 task={}", task.getId(), e);
      writeFallbackTitle(task);
    }
  }

  /** 加载首轮完整对话（首条 User + 对应 Assistant），与 loadRecentContext(1) 同构。 */
  private List<ChatMessage> loadFirstTurn(long conversationId) {
    return conversationService.loadRecentContext(conversationId, 1);
  }

  /** 用 Utility Model 生成一句话中文标题；失败返回 null（不抛业务异常）。 */
  private String generateTitle(long conversationId, List<ChatMessage> firstTurn) {
    ModelConfigRevision revision;
    try {
      revision = modelConfigService.resolveUtilityRevisionForRouting();
    } catch (RuntimeException e) {
      log.warn("Utility 模型不可用，回退首条消息截断 conversation={}", conversationId, e);
      return null;
    }
    try {
      ChatModel model = modelClientFactory.create(revision);
      ChatResponse response =
          modelClientFactory.callWithTotalTimeout(
              () -> model.call(new Prompt(new UserMessage(buildPrompt(firstTurn)))),
              revision.getId());
      String text = modelClientFactory.extractText(response);
      return sanitizeTitle(text);
    } catch (RuntimeException e) {
      // 调用失败/超时/输出非法：静默回退
      log.warn("Utility 模型生成标题失败，回退首条消息截断 conversation={}", conversationId, e);
      return null;
    }
  }

  private String buildPrompt(List<ChatMessage> firstTurn) {
    StringBuilder dialog = new StringBuilder();
    for (ChatMessage m : firstTurn) {
      String content = m.getContent();
      if (content == null || content.isBlank()) {
        continue;
      }
      dialog
          .append("[")
          .append(ChatMessage.ROLE_USER.equals(m.getRole()) ? "用户" : "助手")
          .append("] ")
          .append(content)
          .append('\n');
    }
    return "你是会话标题生成助手。根据下面的首轮对话，用一句话为整个会话起一个标题。\n"
        + "要求：\n"
        + "- 只输出标题本身，不要解释、不要加引号、不要换行、不要序号前缀。\n"
        + "- 中文，10～20 字为宜。\n"
        + "- 概括对话主题，不要照抄原文。\n\n"
        + "首轮对话：\n"
        + (dialog.length() == 0 ? "(空)" : dialog.toString());
  }

  /** 清洗并截断模型输出：去换行/首尾空白、压缩空行、超长截断；空输出返回 null。 */
  private String sanitizeTitle(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String cleaned = text.replaceAll("\\r?\\n", " ").replaceAll("\\s+", " ").trim();
    // 去掉可能包裹的引号
    if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
      cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
    }
    if (cleaned.isEmpty()) {
      return null;
    }
    return cleaned.length() > MAX_TITLE_LENGTH ? cleaned.substring(0, MAX_TITLE_LENGTH) : cleaned;
  }

  /** 回退标题：首条 User Message 内容前 ~30 字符（去换行）。无可用消息时返回占位符。 */
  private String fallbackTitle(List<ChatMessage> firstTurn) {
    for (ChatMessage m : firstTurn) {
      if (!ChatMessage.ROLE_USER.equals(m.getRole()) || m.getContent() == null) {
        continue;
      }
      String content = m.getContent().replaceAll("\\r?\\n", " ").trim();
      if (content.isEmpty()) {
        continue;
      }
      return content.length() > FALLBACK_TITLE_LENGTH
          ? content.substring(0, FALLBACK_TITLE_LENGTH)
          : content;
    }
    return ConversationService.TITLE_PLACEHOLDER;
  }

  /** 原子写入：仅当标题仍为占位时生效，不覆盖用户生成期间的手动改名。 */
  private void writeTitleIfStillPlaceholder(long conversationId, String title) {
    conversationService.setTitleIfPlaceholder(conversationId, title);
  }

  /** 异常兜底：直接按首条消息截断写入（不覆盖手动改名）。 */
  private void writeFallbackTitle(ProcessingTask task) {
    try {
      TitleTaskPayload payload = objectMapper.readValue(task.getPayload(), TitleTaskPayload.class);
      List<ChatMessage> firstTurn = loadFirstTurn(payload.conversationId());
      conversationService.setTitleIfPlaceholder(payload.conversationId(), fallbackTitle(firstTurn));
    } catch (Exception ex) {
      log.warn("会话标题回退写入失败 task={}", task.getId(), ex);
    }
  }

  /** 任务 payload：仅携带会话 id。 */
  public record TitleTaskPayload(long conversationId) {}
}
