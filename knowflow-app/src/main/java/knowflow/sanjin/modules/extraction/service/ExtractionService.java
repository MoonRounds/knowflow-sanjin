package knowflow.sanjin.modules.extraction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.config.ExtractionProperties;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.exception.ExtractionInputOverBudgetException;
import knowflow.sanjin.modules.extraction.exception.ExtractionNoCompletedMessagesException;
import knowflow.sanjin.modules.extraction.exception.ExtractionTaskNotFoundException;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeExtractionTaskMapper;
import knowflow.sanjin.modules.modelconfig.entity.ModelConfigRevision;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提取触发应用服务：cutoff 快照、预算校验、幂等去重与任务提交的事务边界。
 *
 * <p>触发时在事务内完成：读取截至触发点的全部完整 active Turns（仅 COMPLETED 且 isActive 的 Assistant 及其 User， 复用 {@link
 * ConversationService#loadRecentContext} 的同构判定），计算输入字符数，超预算直接抛异常（不创建任务、不调用 LLM）。 幂等：同 owner /
 * conversation / cutoff / profile+version / utility revision
 * 只创建一个任务；活动任务（PENDING/PROCESSING）重复点击返回同一任务， 终态任务同 cutoff 返回旧任务、cutoff 变化则创建新任务。任务只保存范围与身份引用，不复制
 * Conversation 正文。
 */
@Service
public class ExtractionService {

  private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

  private final CurrentOwnerProvider currentOwnerProvider;
  private final ConversationService conversationService;
  private final ModelConfigService modelConfigService;
  private final TaskSubmissionService taskSubmissionService;
  private final KnowledgeExtractionTaskMapper taskMapper;
  private final ExtractionProperties properties;

  public ExtractionService(
      CurrentOwnerProvider currentOwnerProvider,
      ConversationService conversationService,
      ModelConfigService modelConfigService,
      TaskSubmissionService taskSubmissionService,
      KnowledgeExtractionTaskMapper taskMapper,
      ExtractionProperties properties) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.conversationService = conversationService;
    this.modelConfigService = modelConfigService;
    this.taskSubmissionService = taskSubmissionService;
    this.taskMapper = taskMapper;
    this.properties = properties;
  }

  /** 触发一次提取；返回任务快照。幂等与预算校验在事务内完成。 */
  @Transactional
  public KnowledgeExtractionTask trigger(Long conversationId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();

    // 截止点为当前最后一个消息；loadRecentContext 会校验会话存在且未软删。
    Long cutoffMessageId = conversationService.lastMessageId(conversationId, ownerId);
    if (cutoffMessageId == null) {
      throw new ExtractionNoCompletedMessagesException(conversationId);
    }

    // 提取输入 = 截至 cutoff 的全部完整 active Turns（与 Memory 同构判定，失败轮次整体排除）
    List<ChatMessage> turns =
        conversationService.loadAllTurnsUpTo(conversationId, ownerId, cutoffMessageId);
    if (turns.isEmpty()) {
      throw new ExtractionNoCompletedMessagesException(conversationId);
    }
    int inputChars =
        turns.stream().mapToInt(m -> m.getContent() == null ? 0 : m.getContent().length()).sum();
    if (inputChars > properties.getInputCharBudget()) {
      throw new ExtractionInputOverBudgetException(inputChars, properties.getInputCharBudget());
    }

    ModelConfigRevision utility = modelConfigService.resolveUtilityRevisionForRouting();
    String profile = ExtractionConstants.EXTRACTION_PROFILE;
    int profileVersion = ExtractionConstants.EXTRACTION_PROFILE_VERSION;
    String businessKey =
        businessKey(conversationId, cutoffMessageId, utility.getId(), profileVersion);

    // 幂等：先查已存在任务快照（同键唯一约束兜底并发）
    KnowledgeExtractionTask existing =
        findByDedupKey(
            ownerId, conversationId, cutoffMessageId, profile, profileVersion, utility.getId());
    if (existing != null) {
      return existing;
    }

    ProcessingTask task =
        taskSubmissionService.submit(
            ExtractionConstants.TASK_TYPE_EXTRACTION,
            businessKey,
            cutoffMessageId,
            ownerId,
            null,
            properties.getMaxRetries(),
            ExtractionConstants.WORK_QUEUE_BASE);

    KnowledgeExtractionTask snapshot = new KnowledgeExtractionTask();
    snapshot.setOwnerId(ownerId);
    snapshot.setConversationId(conversationId);
    snapshot.setCutoffMessageId(cutoffMessageId);
    snapshot.setExtractionProfile(profile);
    snapshot.setProfileVersion(profileVersion);
    snapshot.setUtilityRevisionId(utility.getId());
    snapshot.setProcessingTaskId(task.getId());
    snapshot.setInputCharCount(inputChars);
    try {
      taskMapper.insert(snapshot);
    } catch (DuplicateKeyException e) {
      // 并发同键已创建：返回已有快照（幂等）
      log.debug("并发触发提取去重，会话 {}", conversationId);
      return findByDedupKey(
          ownerId, conversationId, cutoffMessageId, profile, profileVersion, utility.getId());
    }
    log.info(
        "已触发提取 conversation={} cutoff={} inputChars={} task={} snapshot={}",
        conversationId,
        cutoffMessageId,
        inputChars,
        task.getId(),
        snapshot.getId());
    return snapshot;
  }

  /** 查询并校验任务快照（owner 过滤）。 */
  @Transactional(readOnly = true)
  public KnowledgeExtractionTask getSnapshot(Long id) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeExtractionTask snapshot =
        taskMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeExtractionTask>()
                .eq(KnowledgeExtractionTask::getId, id)
                .eq(KnowledgeExtractionTask::getOwnerId, ownerId));
    if (snapshot == null) {
      throw new ExtractionTaskNotFoundException(id);
    }
    return snapshot;
  }

  private KnowledgeExtractionTask findByDedupKey(
      long ownerId,
      Long conversationId,
      Long cutoffMessageId,
      String profile,
      int profileVersion,
      Long utilityRevisionId) {
    return taskMapper.selectOne(
        new LambdaQueryWrapper<KnowledgeExtractionTask>()
            .eq(KnowledgeExtractionTask::getOwnerId, ownerId)
            .eq(KnowledgeExtractionTask::getConversationId, conversationId)
            .eq(KnowledgeExtractionTask::getCutoffMessageId, cutoffMessageId)
            .eq(KnowledgeExtractionTask::getExtractionProfile, profile)
            .eq(KnowledgeExtractionTask::getProfileVersion, profileVersion)
            .eq(KnowledgeExtractionTask::getUtilityRevisionId, utilityRevisionId)
            .last("LIMIT 1"));
  }

  private static String businessKey(
      Long conversationId, Long cutoffMessageId, Long utilityRevisionId, int profileVersion) {
    return ExtractionConstants.BUSINESS_KEY_PREFIX
        + conversationId
        + ExtractionConstants.BUSINESS_KEY_DELIMITER
        + cutoffMessageId
        + ExtractionConstants.BUSINESS_KEY_DELIMITER
        + utilityRevisionId
        + ExtractionConstants.BUSINESS_KEY_DELIMITER
        + profileVersion;
  }
}
