package knowflow.sanjin.modules.extraction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.config.ExtractionProperties;
import knowflow.sanjin.modules.extraction.dto.ExtractionResult;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.exception.TerminalExtractionException;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeExtractionTaskMapper;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提取执行器：读取快照范围的消息 → 构建提取 Prompt → Structured Output → 严格校验 → 落候选。
 *
 * <p>校验规则（违反即判输出非法，最多修复一次）：候选 0～maxCandidates 个；每个候选必须有非空 title/content；推荐 KnowledgeBase 必须 是当前
 * owner 已存在且属于候选目录（AI 只能建议已有 KB，不能自动创建）；每个候选 KB ≤3、tags ≤5；非法 KB id / 重复 id 拒绝。 0 候选是成功结果， 不是失败。
 */
@Service
public class ExtractionExecutor {

  private static final Logger log = LoggerFactory.getLogger(ExtractionExecutor.class);

  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeExtractionTaskMapper taskMapper;
  private final KnowledgeCandidateMapper candidateMapper;
  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final ConversationService conversationService;
  private final ModelClientFactory modelClientFactory;
  private final ModelConfigService modelConfigService;
  private final ExtractionProperties properties;

  public ExtractionExecutor(
      CurrentOwnerProvider currentOwnerProvider,
      KnowledgeExtractionTaskMapper taskMapper,
      KnowledgeCandidateMapper candidateMapper,
      KnowledgeBaseMapper knowledgeBaseMapper,
      ConversationService conversationService,
      ModelClientFactory modelClientFactory,
      ModelConfigService modelConfigService,
      ExtractionProperties properties) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.taskMapper = taskMapper;
    this.candidateMapper = candidateMapper;
    this.knowledgeBaseMapper = knowledgeBaseMapper;
    this.conversationService = conversationService;
    this.modelClientFactory = modelClientFactory;
    this.modelConfigService = modelConfigService;
    this.properties = properties;
  }

  /** Consumer 入口：按 processing_task_id 查询快照（owner 校验）与输入消息后执行。快照缺失视为终态失败。 */
  @Transactional
  public int executeWithLookup(ProcessingTask task) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeExtractionTask snapshot =
        taskMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeExtractionTask>()
                .eq(KnowledgeExtractionTask::getProcessingTaskId, task.getId())
                .eq(KnowledgeExtractionTask::getOwnerId, ownerId));
    if (snapshot == null) {
      throw new TerminalExtractionException(
          ErrorCode.EXTRACTION_TASK_NOT_FOUND,
          "Extraction snapshot not found for task " + task.getId());
    }
    List<ChatMessage> turns =
        loadInputMessages(
            snapshot.getConversationId(), snapshot.getOwnerId(), snapshot.getCutoffMessageId());
    return execute(task, snapshot, turns);
  }

  /** 按 cutoff 读取输入消息（仅 cutoff 之前且完整 active Turns，与触发时同构判定）。 */
  @Transactional(readOnly = true)
  public List<ChatMessage> loadInputMessages(
      Long conversationId, Long ownerId, Long cutoffMessageId) {
    return conversationService.loadAllTurnsUpTo(conversationId, ownerId, cutoffMessageId);
  }

  /** 执行一次提取。成功（含 0 候选）返回候选数；可重试故障抛 RetryableExtractionException，终态抛 TerminalExtractionException。 */
  @Transactional
  public int execute(
      ProcessingTask task, KnowledgeExtractionTask snapshot, List<ChatMessage> turns) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    List<KnowledgeBase> catalog = loadEnabledKnowledgeBases(ownerId);
    if (catalog.isEmpty()) {
      log.info("提取任务 {}：无启用的知识库，返回 0 个候选", task.getId());
      return 0;
    }

    ModelConfigRevision utility =
        modelConfigService.getRevisionForSnapshot(snapshot.getUtilityRevisionId());
    BeanOutputConverter<ExtractionResult> converter =
        new BeanOutputConverter<>(ExtractionResult.class);
    String basePrompt = buildPrompt(turns, catalog, converter.getFormat(), properties);
    ChatModel model = modelClientFactory.create(utility);

    String text = callExtraction(model, basePrompt, utility.getId());
    ExtractionResult parsed;
    try {
      parsed = converter.convert(text);
    } catch (RuntimeException e) {
      parsed = null;
    }
    if (!isValid(parsed)) {
      // 修复一次：修复 Prompt 只含 schema 约束，不含正文/问题原文
      String fixPrompt =
          "你的上一次输出不符合要求。候选必须为 0～"
              + properties.getMaxCandidates()
              + " 个；每个候选必须有非空 title 与 content；"
              + "knowledgeBaseId 必须为给定目录内合法 id（单归属）或为空；tags 不超过 "
              + properties.getMaxTagsPerCandidate()
              + " 个；不得包含目录之外的 id。请重新输出严格 JSON。\n\n"
              + converter.getFormat();
      String fixedText = callExtraction(model, fixPrompt, utility.getId());
      try {
        parsed = converter.convert(fixedText);
      } catch (RuntimeException e) {
        parsed = null;
      }
      if (!isValid(parsed)) {
        throw new TerminalExtractionException(
            ErrorCode.INDEX_SCHEMA_FAILURE, "Extraction structured output invalid after one fix");
      }
    }

    List<ExtractionResult.Candidate> candidates =
        parsed != null ? parsed.getCandidates() : List.of();
    int count = persistCandidates(snapshot, candidates, ownerId);
    taskMapper.update(
        null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                KnowledgeExtractionTask>()
            .eq(KnowledgeExtractionTask::getId, snapshot.getId())
            .set(KnowledgeExtractionTask::getCandidateCount, count));
    log.info("提取任务 {} 生成 {} 个候选", task.getId(), count);
    return count;
  }

  @Transactional
  public int persistCandidates(
      KnowledgeExtractionTask snapshot, List<ExtractionResult.Candidate> candidates, long ownerId) {
    if (candidates.isEmpty()) {
      return 0;
    }
    for (ExtractionResult.Candidate c : candidates) {
      KnowledgeCandidate entity = new KnowledgeCandidate();
      entity.setOwnerId(ownerId);
      entity.setExtractionTaskId(snapshot.getId());
      entity.setStatus(ExtractionConstants.CANDIDATE_PENDING);
      entity.setAiTitle(c.getTitle());
      entity.setAiSummary(c.getSummary());
      entity.setAiContent(c.getContent());
      entity.setAiKnowledgeBaseId(c.getKnowledgeBaseId());
      entity.setAiTags(joinIds(c.getTags()));
      entity.setAiReason(c.getReason());
      // 草稿初始化复制 AI 原值（快照合并语义）
      entity.setDraftTitle(c.getTitle());
      entity.setDraftSummary(c.getSummary());
      entity.setDraftContent(c.getContent());
      entity.setDraftKnowledgeBaseId(c.getKnowledgeBaseId());
      entity.setDraftTags(joinIds(c.getTags()));
      entity.setRowVersion(0);
      candidateMapper.insert(entity);
    }
    return candidates.size();
  }

  /** 校验提取输出：候选数量、必填字段、KB 目录内且 ≤3、tags ≤5、无重复 id。 */
  public boolean isValid(ExtractionResult result) {
    if (result == null) {
      return false;
    }
    List<ExtractionResult.Candidate> candidates = result.getCandidates();
    if (candidates == null) {
      return false;
    }
    if (candidates.size() > properties.getMaxCandidates()) {
      return false;
    }
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    Set<Long> catalogIds = new LinkedHashSet<>();
    knowledgeBaseMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getOwnerId, ownerId)
                .eq(KnowledgeBase::getDeleted, false)
                .eq(KnowledgeBase::getEnabled, true))
        .forEach(kb -> catalogIds.add(kb.getId()));
    for (ExtractionResult.Candidate c : candidates) {
      if (c.getTitle() == null || c.getTitle().isBlank()) {
        return false;
      }
      if (c.getContent() == null || c.getContent().isBlank()) {
        return false;
      }
      String kbId = c.getKnowledgeBaseId();
      if (kbId != null && !kbId.isBlank()) {
        Long id;
        try {
          id = Long.valueOf(kbId.trim());
        } catch (NumberFormatException e) {
          return false;
        }
        if (!catalogIds.contains(id)) {
          return false;
        }
      }
      List<String> tags = c.getTags();
      if (tags != null && tags.size() > properties.getMaxTagsPerCandidate()) {
        return false;
      }
    }
    return true;
  }

  private List<KnowledgeBase> loadEnabledKnowledgeBases(long ownerId) {
    return knowledgeBaseMapper.selectList(
        new LambdaQueryWrapper<KnowledgeBase>()
            .eq(KnowledgeBase::getOwnerId, ownerId)
            .eq(KnowledgeBase::getDeleted, false)
            .eq(KnowledgeBase::getEnabled, true)
            .orderByAsc(KnowledgeBase::getDisplayName));
  }

  private String buildPrompt(
      List<ChatMessage> turns,
      List<KnowledgeBase> catalog,
      String schemaFormat,
      ExtractionProperties properties) {
    StringBuilder dialog = new StringBuilder();
    for (ChatMessage m : turns) {
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
    StringBuilder kbText = new StringBuilder();
    for (KnowledgeBase kb : catalog) {
      kbText.append("- ").append(kb.getId()).append(": ").append(kb.getDisplayName());
      if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
        kbText.append(" — ").append(kb.getDescription());
      }
      kbText.append('\n');
    }
    return "你是个人知识沉淀助手。从下面的完整对话中提取值得长期沉淀的知识候选。\n"
        + "要求：\n"
        + "- 只提取有长期复用价值的事实、结论、方法论或代码片段；纯寒暄、临时性问题不提取。\n"
        + "- 每个候选独立成条；不要合并或拆分语义单元。\n"
        + "- 候选数量 0～"
        + properties.getMaxCandidates()
        + " 个；没有值得沉淀的内容时输出空数组（这是正常结果）。\n"
        + "- title：简短标题（≤120 字符）。\n"
        + "- summary：一句话摘要。\n"
        + "- content：Markdown 正文，保留关键细节。\n"
        + "- knowledgeBaseId：只从给定目录选择最匹配的 1 个（单归属）；不可创建新目录。\n"
        + "- tags：0～"
        + properties.getMaxTagsPerCandidate()
        + " 个标签。\n"
        + "- reason：沉淀理由。\n\n"
        + "可选的现有知识库目录（id: name — description）：\n"
        + kbText
        + "\n完整对话：\n"
        + dialog
        + "\n\n输出严格 JSON，符合以下 schema：\n"
        + schemaFormat;
  }

  private String callExtraction(ChatModel model, String prompt, long revisionId) {
    ChatResponse response =
        modelClientFactory.callWithTotalTimeout(
            () -> model.call(new Prompt(new UserMessage(prompt))), revisionId);
    String text = modelClientFactory.extractText(response);
    if (text == null || text.isBlank()) {
      throw new TerminalExtractionException(
          ErrorCode.INDEX_SCHEMA_FAILURE, "Extraction returned empty output");
    }
    return text;
  }

  private static String joinIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return "";
    }
    return ids.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .collect(Collectors.joining(","));
  }
}
