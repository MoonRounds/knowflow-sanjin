package knowflow.sanjin.modules.extraction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocumentTag;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentTagMapper;
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
 * Candidate 确认应用服务：幂等地把草稿转换为 KnowledgeDocument 并提交索引任务。
 *
 * <p>幂等（DECISIONS §11）：每个 Candidate 至多创建一个 Document，由 {@code knowledge_document.candidate_id}
 * 唯一约束保证； 并发双击时只有首个事务成功， 其余捕获 DuplicateKeyException 返回已创建的 Document。确认事务内创建 Document、单归属 KB（ADR
 * 0007）、Tag 关系与索引 ProcessingTask， 失败整体回滚不留半成品。索引复用 Phase 05 的 KnowledgeDocument 链路（FULL 索引）。
 */
@Service
public class CandidateConfirmService {

  private static final int DEFAULT_MAX_RETRIES = 3;

  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeCandidateMapper candidateMapper;
  private final KnowledgeDocumentMapper documentMapper;
  private final KnowledgeDocumentTagMapper documentTagMapper;
  private final TagMapper tagMapper;
  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final KnowledgeBaseService knowledgeBaseService;
  private final TaskSubmissionService taskSubmissionService;

  public CandidateConfirmService(
      CurrentOwnerProvider currentOwnerProvider,
      KnowledgeCandidateMapper candidateMapper,
      KnowledgeDocumentMapper documentMapper,
      KnowledgeDocumentTagMapper documentTagMapper,
      TagMapper tagMapper,
      KnowledgeBaseMapper knowledgeBaseMapper,
      KnowledgeBaseService knowledgeBaseService,
      TaskSubmissionService taskSubmissionService) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.candidateMapper = candidateMapper;
    this.documentMapper = documentMapper;
    this.documentTagMapper = documentTagMapper;
    this.tagMapper = tagMapper;
    this.knowledgeBaseMapper = knowledgeBaseMapper;
    this.knowledgeBaseService = knowledgeBaseService;
    this.taskSubmissionService = taskSubmissionService;
  }

  /**
   * 确认候选：PENDING → CONFIRMED 并创建 Document。幂等：已 CONFIRMED 且已有 Document 时返回原候选（不重复创建）。 并发双击由唯一约束兜底。
   */
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

    // 校验草稿（确认保存用户最终编辑内容）：必须归属一个知识库，正文非空
    Long kbId = parseSingleKbId(candidate.getDraftKnowledgeBaseId());
    if (kbId == null) {
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
    knowledgeBaseService.getByIdAndOwner(kbId); // 校验存在与 owner 边界

    List<String> tagNames = normalizeTags(splitTags(candidate.getDraftTags()));

    // 创建 KnowledgeDocument（来源 AI_CONVERSATION，单归属 KB，Candidate 来源关系）
    KnowledgeDocument document = new KnowledgeDocument();
    document.setOwnerId(ownerId);
    document.setKbId(kbId);
    document.setSourceType(ExtractionConstants.SOURCE_AI_CONVERSATION);
    document.setCandidateId(candidateId);
    document.setTitle(candidate.getDraftTitle().trim());
    document.setSummary(candidate.getDraftSummary());
    document.setContent(candidate.getDraftContent());
    document.setContentVersion(1);
    document.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    document.setDeleted(false);
    document.setRowVersion(0);
    try {
      documentMapper.insert(document);
    } catch (DuplicateKeyException e) {
      // 并发确认：另一个事务已用此候选创建了 Document，返回已确认候选（幂等）
      KnowledgeCandidate alreadyConfirmed = getByIdAndOwner(candidateId, ownerId);
      return alreadyConfirmed;
    }

    replaceTagRelations(ownerId, document.getId(), tagNames);

    // 确认状态 + 记录确认时间；并发 reject/confirm 竞争时条件更新可能影响 0 行，此时回滚整个事务（候选状态已非 PENDING，Document 不得残留）
    int statusUpdated =
        candidateMapper.update(
            null,
            new LambdaUpdateWrapper<KnowledgeCandidate>()
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

    submitIndexTask(document.getId(), ownerId, 1);
    return getByIdAndOwner(candidateId, ownerId);
  }

  /** 已确认候选对应的 Document id；未确认或未创建返回 null。 */
  @Transactional(readOnly = true)
  public String findConfirmedItemId(Long candidateId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeDocument document =
        documentMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getCandidateId, candidateId)
                .eq(KnowledgeDocument::getOwnerId, ownerId));
    return document != null ? String.valueOf(document.getId()) : null;
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

  /** 单归属 KB 解析：草稿列为单值（可空）；空/空白 → null（确认时拒绝）。 */
  private static Long parseSingleKbId(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();
    return Long.valueOf(trimmed);
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

  private void replaceTagRelations(long ownerId, Long itemId, List<String> newNames) {
    for (String name : newNames) {
      Long tagId = findOrCreateTag(ownerId, name);
      KnowledgeDocumentTag rel = new KnowledgeDocumentTag();
      rel.setOwnerId(ownerId);
      rel.setKnowledgeDocumentId(itemId);
      rel.setTagId(tagId);
      rel.setDeleted(false);
      documentTagMapper.insert(rel);
    }
  }

  private Long findOrCreateTag(long ownerId, String normalizedName) {
    Tag tag =
        tagMapper.selectOne(
            new LambdaQueryWrapper<Tag>()
                .eq(Tag::getOwnerId, ownerId)
                .eq(Tag::getNormalizedName, normalizedName)
                .eq(Tag::getDeleted, false));
    if (tag != null) {
      return tag.getId();
    }
    Tag fresh = new Tag();
    fresh.setOwnerId(ownerId);
    fresh.setName(normalizedName);
    fresh.setNormalizedName(normalizedName);
    fresh.setDeleted(false);
    try {
      tagMapper.insert(fresh);
      return fresh.getId();
    } catch (DuplicateKeyException e) {
      return tagMapper
          .selectOne(
              new LambdaQueryWrapper<Tag>()
                  .eq(Tag::getOwnerId, ownerId)
                  .eq(Tag::getNormalizedName, normalizedName)
                  .eq(Tag::getDeleted, false))
          .getId();
    }
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
