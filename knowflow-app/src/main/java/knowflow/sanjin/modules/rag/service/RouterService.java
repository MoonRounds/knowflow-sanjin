package knowflow.sanjin.modules.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import knowflow.sanjin.modules.conversation.memory.MemoryService;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.rag.dto.RoutableKnowledgeBase;
import knowflow.sanjin.modules.rag.dto.RouterResult;
import knowflow.sanjin.modules.rag.dto.RouterTrace;
import knowflow.sanjin.modules.rag.exception.RouterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * Knowledge Router：用 Utility Model 结构化输出判断是否需要 RAG 并选择 0～3 个 KnowledgeBase。
 *
 * <p>仅向 Router 提供当前 owner 下 enabled 且「至少有一个可检索 Item」的 KnowledgeBase 目录（DECISIONS §13）。输入为当前问题 + 最近
 * N 轮紧凑上下文。非法输出最多修复一次；仍失败抛 {@link RouterException} 由调用方降级为普通回答。
 *
 * <p>修复 Prompt 只包含 schema 格式说明，绝不携带笔记正文或问题原文，避免把不可信内容引入修复阶段。route score 仅用于诊断。
 */
@Service
public class RouterService {

  private static final Logger log = LoggerFactory.getLogger(RouterService.class);

  private static final int MAX_KNOWLEDGE_BASES = 3;
  public static final String MODE_AUTO = "AUTO";
  public static final String MODE_MANUAL = "MANUAL";

  private final RagProperties properties;
  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final KnowledgeDocumentMapper documentMapper;
  private final ModelConfigService modelConfigService;
  private final ModelClientFactory modelClientFactory;
  private final MemoryService memoryService;

  public RouterService(
      RagProperties properties,
      CurrentOwnerProvider currentOwnerProvider,
      KnowledgeBaseMapper knowledgeBaseMapper,
      KnowledgeDocumentMapper documentMapper,
      ModelConfigService modelConfigService,
      ModelClientFactory modelClientFactory,
      MemoryService memoryService) {
    this.properties = properties;
    this.currentOwnerProvider = currentOwnerProvider;
    this.knowledgeBaseMapper = knowledgeBaseMapper;
    this.documentMapper = documentMapper;
    this.modelConfigService = modelConfigService;
    this.modelClientFactory = modelClientFactory;
    this.memoryService = memoryService;
  }

  /**
   * 执行一次路由。返回带 {@code needRag} 与选中 KB 的 {@link RouterResult}。目录为空或 Utility 不可用 / Router 失败时 {@code
   * result} 为 null，由调用方标记 {@code NOT_AVAILABLE} 或 {@code DEGRADED} 并跳过检索。
   *
   * <p>手动绑定非空时只向 Router 暴露该范围；空集合保持原 AUTO 行为。
   */
  public RouterOutcome route(
      Long conversationId, String userQuestion, List<Long> boundKnowledgeBaseIds) {
    boolean manual = boundKnowledgeBaseIds != null && !boundKnowledgeBaseIds.isEmpty();
    String mode = manual ? MODE_MANUAL : MODE_AUTO;
    List<RoutableKnowledgeBase> catalog =
        manual ? buildManualCatalog(boundKnowledgeBaseIds) : buildAutoCatalog();
    if (catalog.isEmpty()) {
      RouterTrace trace = RouterTrace.catalogOnly(catalog);
      trace.setMode(mode);
      return new RouterOutcome(null, trace);
    }

    ModelConfigRevision utilityRevision;
    try {
      utilityRevision = resolveUtilityRevision();
    } catch (RuntimeException e) {
      // Utility 未设置/被禁用/未通过能力测试：跳过调用，普通回答 + NOT_AVAILABLE
      RouterTrace trace = RouterTrace.failed(catalog, "utility-unavailable");
      trace.setMode(mode);
      return new RouterOutcome(null, trace);
    }

    RouterTrace trace = new RouterTrace();
    trace.setMode(mode);
    trace.setCatalog(catalog);
    trace.setRouterCalled(true);

    ChatModel model = modelClientFactory.create(utilityRevision);
    BeanOutputConverter<RouterResult> converter = new BeanOutputConverter<>(RouterResult.class);
    String context = buildContext(conversationId, userQuestion);
    String basePrompt = buildPrompt(catalog, userQuestion, context, converter.getFormat());

    String text;
    try {
      text = callRouter(model, basePrompt, utilityRevision.getId());
      RouterResult parsed = parse(converter, text);
      if (!isValid(parsed, catalog)) {
        trace.setFixed(true);
        String fixPrompt =
            "你的上一次输出不符合要求：needRag 必须为布尔值，knowledgeBaseIds 必须为 0～3 个且在给定目录 id 中。"
                + "请重新输出严格 JSON。\n\n"
                + converter.getFormat();
        String fixedText = callRouter(model, fixPrompt, utilityRevision.getId());
        parsed = parse(converter, fixedText);
        if (!isValid(parsed, catalog)) {
          throw new RouterException("Router output invalid after fix");
        }
      }
      trace.setResult(parsed);
      return new RouterOutcome(parsed, trace);
    } catch (RouterException e) {
      log.warn("Router failed for conversation {}: {}", conversationId, e.getMessage());
      trace.setFailure("router-failed");
      return new RouterOutcome(null, trace);
    }
  }

  /** 路由结果 + 诊断；{@code result} 为 null 表示不可用或失败（普通回答）。 */
  public record RouterOutcome(RouterResult result, RouterTrace trace) {

    public boolean available() {
      return result != null;
    }
  }

  /** 构建「当前 owner 下 enabled 且至少有一个可检索 Document」的 KnowledgeBase 目录，按名称排序后截断到上限。 */
  private List<RoutableKnowledgeBase> buildAutoCatalog() {
    return buildCatalog(null, true);
  }

  private List<RoutableKnowledgeBase> buildManualCatalog(List<Long> boundKnowledgeBaseIds) {
    return buildCatalog(new LinkedHashSet<>(boundKnowledgeBaseIds), false);
  }

  private List<RoutableKnowledgeBase> buildCatalog(Set<Long> allowedIds, boolean applyLimit) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    List<KnowledgeBase> enabled =
        knowledgeBaseMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getOwnerId, ownerId)
                .eq(KnowledgeBase::getDeleted, false)
                .eq(KnowledgeBase::getEnabled, true)
                .in(allowedIds != null && !allowedIds.isEmpty(), KnowledgeBase::getId, allowedIds));
    if (enabled.isEmpty()) {
      return List.of();
    }
    // manual 范围的服务层契约：即使查询返回范围外行（如单测 stub 不按 .in 过滤），也只暴露绑定集合内的库
    if (allowedIds != null) {
      enabled = enabled.stream().filter(kb -> allowedIds.contains(kb.getId())).toList();
    }
    if (enabled.isEmpty()) {
      return List.of();
    }
    Set<Long> enabledIds = new LinkedHashSet<>();
    enabled.forEach(kb -> enabledIds.add(kb.getId()));

    // 可路由 KB：有活跃且已成功索引 Document（kb_id IN enabledIds，未软删，indexedVersion 非空）的 KB
    Set<Long> routableIds = new LinkedHashSet<>();
    documentMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getOwnerId, ownerId)
                .eq(KnowledgeDocument::getDeleted, false)
                .isNotNull(KnowledgeDocument::getIndexedVersion)
                .in(KnowledgeDocument::getKbId, enabledIds))
        .forEach(
            document -> {
              if (document.getKbId() != null) {
                routableIds.add(document.getKbId());
              }
            });
    routableIds.retainAll(enabledIds);

    List<RoutableKnowledgeBase> catalog = new ArrayList<>();
    enabled.forEach(
        kb -> {
          if (routableIds.contains(kb.getId())) {
            catalog.add(
                new RoutableKnowledgeBase(kb.getId(), kb.getDisplayName(), kb.getDescription()));
          }
        });
    catalog.sort(Comparator.comparing(RoutableKnowledgeBase::getName));
    if (!applyLimit) {
      return catalog;
    }
    int limit = Math.max(1, properties.getCatalogLimit());
    return catalog.size() > limit ? new ArrayList<>(catalog.subList(0, limit)) : catalog;
  }

  private ModelConfigRevision resolveUtilityRevision() {
    return modelConfigService.resolveUtilityRevisionForRouting();
  }

  private String buildContext(Long conversationId, String userQuestion) {
    // 最近 N 轮完整 active Turns + 当前问题，紧凑拼接后按字符上限截断
    StringBuilder sb = new StringBuilder();
    List<Message> window;
    try {
      window = memoryService.loadWindow(conversationId);
    } catch (RuntimeException e) {
      window = List.of();
    }
    for (Message m : window) {
      appendMessage(sb, m);
    }
    appendMessage(sb, new UserMessage(userQuestion));
    String text = sb.toString().trim();
    int limit = Math.max(100, properties.getRouterContextCharLimit());
    return text.length() > limit ? text.substring(text.length() - limit) : text;
  }

  private static void appendMessage(StringBuilder sb, Message m) {
    String content = m.getText();
    if (content == null || content.isBlank()) {
      return;
    }
    sb.append("[").append(m.getMessageType()).append("] ").append(content).append('\n');
  }

  private String buildPrompt(
      List<RoutableKnowledgeBase> catalog,
      String userQuestion,
      String context,
      String schemaFormat) {
    StringBuilder catalogText = new StringBuilder();
    for (RoutableKnowledgeBase kb : catalog) {
      catalogText.append("- ").append(kb.getId()).append(": ").append(kb.getName());
      if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
        catalogText.append(" — ").append(kb.getDescription());
      }
      catalogText.append('\n');
    }
    return "你是知识检索路由。判断当前问题是否需要检索个人知识库来回答。\n"
        + "可路由知识库目录（id: name — description）：\n"
        + catalogText
        + "\n最近上下文：\n"
        + (context.isBlank() ? "(无)" : context)
        + "\n当前问题："
        + userQuestion
        + "\n\n"
        + "输出严格 JSON，符合以下 schema：\n"
        + schemaFormat;
  }

  private String callRouter(ChatModel model, String prompt, long revisionId) {
    ChatResponse response =
        modelClientFactory.callWithTotalTimeout(
            () -> model.call(new Prompt(new UserMessage(prompt))), revisionId);
    String text = modelClientFactory.extractText(response);
    if (text == null || text.isBlank()) {
      throw new RouterException("Router returned empty output");
    }
    return text;
  }

  private RouterResult parse(BeanOutputConverter<RouterResult> converter, String text) {
    try {
      return converter.convert(text);
    } catch (RuntimeException e) {
      throw new RouterException("Router output unparseable", e);
    }
  }

  /** 校验 Router 输出：needRag 布尔；knowledgeBaseIds 0～3 且都在目录内；重复去重；非法 ID 拒绝。 */
  private boolean isValid(RouterResult result, List<RoutableKnowledgeBase> catalog) {
    if (result == null) {
      return false;
    }
    List<String> ids = result.getKnowledgeBaseIds();
    if (ids == null || ids.isEmpty()) {
      return true; // needRag=false 时可为空；needRag=true 且空由调用方按 NO_RELEVANT_CONTEXT 处理
    }
    if (ids.size() > MAX_KNOWLEDGE_BASES) {
      return false;
    }
    Set<Long> catalogIds = new LinkedHashSet<>();
    catalog.forEach(kb -> catalogIds.add(kb.getId()));
    for (String raw : ids) {
      Long id;
      try {
        id = Long.valueOf(raw);
      } catch (NumberFormatException e) {
        return false;
      }
      if (!catalogIds.contains(id)) {
        return false;
      }
    }
    return new LinkedHashSet<>(ids).size() == ids.size();
  }
}
