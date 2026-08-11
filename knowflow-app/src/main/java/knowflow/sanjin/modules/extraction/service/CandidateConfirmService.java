package knowflow.sanjin.modules.extraction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.exception.CandidateInvalidStateException;
import knowflow.sanjin.modules.extraction.exception.CandidateNoKnowledgeBaseException;
import knowflow.sanjin.modules.extraction.exception.CandidateNotFoundException;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeBaseItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItemTag;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeBaseItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemTagMapper;
import knowflow.sanjin.modules.knowledge.mapper.TagMapper;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate 确认应用服务：幂等地把草稿转换为 KnowledgeItem 并提交索引任务。
 *
 * <p>幂等（DECISIONS §11）：每个 Candidate 至多创建一个 Item，由 {@code knowledge_item.candidate_id} 唯一约束保证；
 * 并发双击时只有首个事务成功， 其余捕获 DuplicateKeyException 返回已创建的 Item。确认事务内创建 Item、KB 关系、Tag 关系与索引
 * ProcessingTask， 失败整体回滚不留半成品。索引复用 Phase 05 的 KnowledgeItem 链路（FULL 索引）。
 */
@Service
public class CandidateConfirmService {

  private static final int DEFAULT_MAX_RETRIES = 3;

  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeCandidateMapper candidateMapper;
  private final KnowledgeItemMapper itemMapper;
  private final KnowledgeBaseItemMapper kbItemMapper;
  private final KnowledgeItemTagMapper itemTagMapper;
  private final TagMapper tagMapper;
  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final KnowledgeBaseService knowledgeBaseService;
  private final TaskSubmissionService taskSubmissionService;

  public CandidateConfirmService(
      CurrentOwnerProvider currentOwnerProvider,
      KnowledgeCandidateMapper candidateMapper,
      KnowledgeItemMapper itemMapper,
      KnowledgeBaseItemMapper kbItemMapper,
      KnowledgeItemTagMapper itemTagMapper,
      TagMapper tagMapper,
      KnowledgeBaseMapper knowledgeBaseMapper,
      KnowledgeBaseService knowledgeBaseService,
      TaskSubmissionService taskSubmissionService) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.candidateMapper = candidateMapper;
    this.itemMapper = itemMapper;
    this.kbItemMapper = kbItemMapper;
    this.itemTagMapper = itemTagMapper;
    this.tagMapper = tagMapper;
    this.knowledgeBaseMapper = knowledgeBaseMapper;
    this.knowledgeBaseService = knowledgeBaseService;
    this.taskSubmissionService = taskSubmissionService;
  }

  /** 确认候选：PENDING → CONFIRMED 并创建 Item。幂等：已 CONFIRMED 且已有 Item 时返回原候选（不重复创建）。 并发双击由唯一约束兜底。 */
  @Transactional
  public KnowledgeCandidate confirm(Long candidateId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeCandidate candidate = getByIdAndOwner(candidateId, ownerId);

    if (ExtractionConstants.CANDIDATE_CONFIRMED.equals(candidate.getStatus())) {
      return candidate;
    }
    if (!ExtractionConstants.CANDIDATE_PENDING.equals(candidate.getStatus())) {
      throw new CandidateInvalidStateException(
          "Only PENDING candidate can be confirmed, current status=" + candidate.getStatus());
    }

    // 校验草稿（确认保存用户最终编辑内容）：至少一个 KB，正文非空
    List<Long> kbIds = parseIds(candidate.getDraftKnowledgeBaseIds());
    if (kbIds.isEmpty()) {
      throw new CandidateNoKnowledgeBaseException(candidateId);
    }
    if (candidate.getDraftTitle() == null || candidate.getDraftTitle().isBlank()) {
      throw new knowflow.sanjin.modules.extraction.exception.CandidateEmptyDraftException(
          candidateId, "title");
    }
    if (candidate.getDraftContent() == null || candidate.getDraftContent().isBlank()) {
      throw new knowflow.sanjin.modules.extraction.exception.CandidateEmptyDraftException(
          candidateId, "content");
    }
    resolveKnowledgeBaseIds(ownerId, kbIds);

    List<String> tagNames = normalizeTags(splitTags(candidate.getDraftTags()));

    // 创建 KnowledgeItem（来源 AI_CONVERSATION，Candidate 来源关系）
    KnowledgeItem item = new KnowledgeItem();
    item.setOwnerId(ownerId);
    item.setSourceType(ExtractionConstants.SOURCE_AI_CONVERSATION);
    item.setCandidateId(candidateId);
    item.setTitle(candidate.getDraftTitle().trim());
    item.setSummary(candidate.getDraftSummary());
    item.setContent(candidate.getDraftContent());
    item.setContentVersion(1);
    item.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    item.setStatus(KnowledgeConstants.STATUS_ACTIVE);
    item.setRowVersion(0);
    try {
      itemMapper.insert(item);
    } catch (DuplicateKeyException e) {
      // 并发确认：另一个事务已用此候选创建了 Item，返回已确认候选（幂等）
      KnowledgeCandidate alreadyConfirmed = getByIdAndOwner(candidateId, ownerId);
      return alreadyConfirmed;
    }

    replaceKnowledgeBaseRelations(ownerId, item.getId(), kbIds);
    replaceTagRelations(ownerId, item.getId(), tagNames);

    // 确认状态 + 记录确认时间；并发 reject/confirm 竞争时条件更新可能影响 0 行，此时回滚整个事务（候选状态已非 PENDING，Item 不得残留）
    int statusUpdated =
        candidateMapper.update(
            null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<
                    KnowledgeCandidate>()
                .eq(KnowledgeCandidate::getId, candidateId)
                .eq(KnowledgeCandidate::getOwnerId, ownerId)
                .eq(KnowledgeCandidate::getStatus, ExtractionConstants.CANDIDATE_PENDING)
                .set(KnowledgeCandidate::getStatus, ExtractionConstants.CANDIDATE_CONFIRMED)
                .set(KnowledgeCandidate::getConfirmedAt, java.time.Instant.now())
                .setSql("row_version = row_version + 1"));
    if (statusUpdated != 1) {
      throw new CandidateInvalidStateException(
          "Candidate " + candidateId + " no longer PENDING, confirm rolled back");
    }

    submitIndexTask(item.getId(), ownerId, 1);
    return getByIdAndOwner(candidateId, ownerId);
  }

  /** 已确认候选对应的 Item id；未确认或未创建返回 null。 */
  @Transactional(readOnly = true)
  public String findConfirmedItemId(Long candidateId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeItem item =
        itemMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeItem>()
                .eq(KnowledgeItem::getCandidateId, candidateId)
                .eq(KnowledgeItem::getOwnerId, ownerId));
    return item != null ? String.valueOf(item.getId()) : null;
  }

  private KnowledgeCandidate getByIdAndOwner(Long id, long ownerId) {
    KnowledgeCandidate candidate =
        candidateMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeCandidate>()
                .eq(KnowledgeCandidate::getId, id)
                .eq(KnowledgeCandidate::getOwnerId, ownerId));
    if (candidate == null) {
      throw new CandidateNotFoundException(id);
    }
    return candidate;
  }

  private void resolveKnowledgeBaseIds(long ownerId, List<Long> kbIds) {
    for (Long id : kbIds) {
      knowledgeBaseService.getByIdAndOwner(id); // 校验存在与 owner 边界
    }
  }

  private void submitIndexTask(Long itemId, long ownerId, int contentVersion) {
    taskSubmissionService.submit(
        ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX,
        KnowledgeConstants.BUSINESS_KEY_PREFIX + itemId + ":" + contentVersion,
        itemId,
        ownerId,
        null,
        DEFAULT_MAX_RETRIES);
  }

  private void replaceKnowledgeBaseRelations(long ownerId, Long itemId, List<Long> newKbIds) {
    for (Long kbId : newKbIds) {
      KnowledgeBaseItem fresh = new KnowledgeBaseItem();
      fresh.setOwnerId(ownerId);
      fresh.setKnowledgeBaseId(kbId);
      fresh.setKnowledgeItemId(itemId);
      fresh.setDeleted(false);
      kbItemMapper.insert(fresh);
    }
  }

  private void replaceTagRelations(long ownerId, Long itemId, List<String> newNames) {
    for (String name : newNames) {
      Long tagId = findOrCreateTag(ownerId, name);
      KnowledgeItemTag rel = new KnowledgeItemTag();
      rel.setOwnerId(ownerId);
      rel.setKnowledgeItemId(itemId);
      rel.setTagId(tagId);
      rel.setDeleted(false);
      itemTagMapper.insert(rel);
    }
  }

  private Long findOrCreateTag(long ownerId, String normalizedName) {
    Tag tag =
        tagMapper.selectOne(
            new LambdaQueryWrapper<Tag>()
                .eq(Tag::getOwnerId, ownerId)
                .eq(Tag::getNormalizedName, normalizedName));
    if (tag != null) {
      return tag.getId();
    }
    Tag fresh = new Tag();
    fresh.setOwnerId(ownerId);
    fresh.setName(normalizedName);
    fresh.setNormalizedName(normalizedName);
    try {
      tagMapper.insert(fresh);
      return fresh.getId();
    } catch (DuplicateKeyException e) {
      return tagMapper
          .selectOne(
              new LambdaQueryWrapper<Tag>()
                  .eq(Tag::getOwnerId, ownerId)
                  .eq(Tag::getNormalizedName, normalizedName))
          .getId();
    }
  }

  private static List<Long> parseIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(Long::valueOf)
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  private static List<String> splitTags(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }

  private static List<String> normalizeTags(List<String> tags) {
    return tags.stream()
        .filter(t -> t != null && !t.isBlank())
        .map(String::trim)
        .map(String::toLowerCase)
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }
}
