package knowflow.sanjin.modules.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import knowflow.sanjin.modules.conversation.entity.GenerationTrace;
import knowflow.sanjin.modules.conversation.mapper.GenerationTraceMapper;
import knowflow.sanjin.modules.rag.dto.RetrievalTrace;
import knowflow.sanjin.modules.rag.dto.RetrievedSource;
import knowflow.sanjin.modules.rag.dto.RouterTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Generation Trace 持久化与读取。
 *
 * <p>消息状态与 trace 在同一事务提交（Tx2）：{@code completeWithTrace}/{@code failWithTrace}/{@code
 * cancelWithTrace} 均为 {@code @Transactional}，内部调用 {@code ConversationService} 的状态更新会加入同一事务，保证「消息
 * COMPLETED 必有 trace」。 历史消息经 {@link #loadByAssistantMessageIds} 重放当次 sources/cited。
 */
@Service
public class GenerationTraceService {

  private final GenerationTraceMapper mapper;
  private final ObjectMapper objectMapper;
  private final ConversationService conversationService;

  public GenerationTraceService(
      GenerationTraceMapper mapper,
      ObjectMapper objectMapper,
      ConversationService conversationService) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
    this.conversationService = conversationService;
  }

  /** 成功终结：消息状态 + trace 同事务。 */
  @Transactional
  public void completeWithTrace(
      long conversationId,
      long assistantMessageId,
      String content,
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      boolean makeActive,
      GenerationTraceSnapshot snapshot) {
    conversationService.completeGeneration(
        conversationId,
        assistantMessageId,
        content,
        promptTokens,
        completionTokens,
        totalTokens,
        makeActive,
        snapshot != null ? snapshot.ragStatus() : null);
    saveTrace(conversationId, assistantMessageId, snapshot);
  }

  /** 失败终结：消息状态 + trace 同事务。 */
  @Transactional
  public void failWithTrace(
      long conversationId,
      long assistantMessageId,
      String content,
      String errorCode,
      GenerationTraceSnapshot snapshot) {
    conversationService.markMessageFailed(
        conversationId,
        assistantMessageId,
        content,
        errorCode,
        snapshot != null ? snapshot.ragStatus() : null);
    saveTrace(conversationId, assistantMessageId, snapshot);
  }

  /** 取消终结：消息状态 + trace 同事务。 */
  @Transactional
  public void cancelWithTrace(
      long conversationId,
      long assistantMessageId,
      String content,
      GenerationTraceSnapshot snapshot) {
    conversationService.markMessageCancelled(
        conversationId,
        assistantMessageId,
        content,
        snapshot != null ? snapshot.ragStatus() : null);
    saveTrace(conversationId, assistantMessageId, snapshot);
  }

  private void saveTrace(
      long conversationId, long assistantMessageId, GenerationTraceSnapshot snapshot) {
    // 覆盖式重新生成会复用同一条 assistant 消息（同 id）；无论本次是否产出 trace 都先删旧记录，
    // 避免 uk_gtrace_message 冲突，并保证重新生成失败/无 RAG 时上一轮的旧来源不会残留。
    // 普通新消息没有旧 trace，删除为空操作。
    mapper.delete(
        new LambdaQueryWrapper<GenerationTrace>()
            .eq(GenerationTrace::getAssistantMessageId, assistantMessageId));
    if (snapshot == null) {
      return;
    }
    long ownerId = conversationService.ownerIdOfConversation(conversationId);
    GenerationTrace trace = new GenerationTrace();
    trace.setAssistantMessageId(assistantMessageId);
    trace.setConversationId(conversationId);
    trace.setOwnerId(ownerId);
    trace.setRagStatus(snapshot.ragStatus());
    List<RetrievedSource> sources = snapshot.sources();
    trace.setSourcesJson(sources.isEmpty() ? null : toJson(sources));
    trace.setRouterJson(toJson(snapshot.routerTrace()));
    trace.setRetrievalJson(toJson(snapshot.retrievalTrace()));
    mapper.insert(trace);
  }

  /** 批量读取 trace（按 assistant_message_id）。供消息列表内嵌 sources。 */
  @Transactional(readOnly = true)
  public Map<Long, GenerationTrace> loadByAssistantMessageIds(List<Long> assistantMessageIds) {
    if (assistantMessageIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, GenerationTrace> result = new LinkedHashMap<>();
    mapper
        .selectList(
            new LambdaQueryWrapper<GenerationTrace>()
                .in(GenerationTrace::getAssistantMessageId, assistantMessageIds))
        .forEach(t -> result.put(t.getAssistantMessageId(), t));
    return result;
  }

  private String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      return null;
    }
  }

  /** 反序列化 sources（供 assembler/SSE 使用）；失败返回空列表。 */
  public List<RetrievedSource> parseSources(String sourcesJson) {
    if (sourcesJson == null || sourcesJson.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(sourcesJson, new TypeReference<List<RetrievedSource>>() {});
    } catch (JacksonException e) {
      return List.of();
    }
  }

  public RouterTrace parseRouter(String routerJson) {
    if (routerJson == null || routerJson.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(routerJson, RouterTrace.class);
    } catch (JacksonException e) {
      return null;
    }
  }

  public RetrievalTrace parseRetrieval(String retrievalJson) {
    if (retrievalJson == null || retrievalJson.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(retrievalJson, RetrievalTrace.class);
    } catch (JacksonException e) {
      return null;
    }
  }
}
