package knowflow.sanjin.modules.extraction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.List;
import knowflow.sanjin.modules.extraction.ExtractionConstants;
import knowflow.sanjin.modules.extraction.dto.UpdateCandidateDraftRequest;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import knowflow.sanjin.modules.extraction.exception.CandidateInvalidStateException;
import knowflow.sanjin.modules.extraction.exception.CandidateNotFoundException;
import knowflow.sanjin.modules.extraction.exception.CandidateVersionConflictException;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate 审核应用服务：列表、详情、草稿编辑、拒绝与恢复。
 *
 * <p>状态迁移（DECISIONS §11）：PENDING → CONFIRMED / REJECTED；REJECTED → PENDING（撤销拒绝）。 CONFIRMED
 * 是终态：不可编辑草稿、不可再次 确认。乐观锁：草稿编辑与状态迁移通过 rowVersion 条件更新保护并发，冲突返回版本冲突错误。
 */
@Service
public class CandidateService {

  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeCandidateMapper candidateMapper;

  public CandidateService(
      CurrentOwnerProvider currentOwnerProvider, KnowledgeCandidateMapper candidateMapper) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.candidateMapper = candidateMapper;
  }

  /** 分页列表：owner 过滤，可按状态筛选；PENDING 优先，其余按创建时间倒序。 */
  @Transactional(readOnly = true)
  public Page<KnowledgeCandidate> listForOwner(String status, long page, long size) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    // 统计条件（不带排序/分页）
    LambdaQueryWrapper<KnowledgeCandidate> countWrapper =
        new LambdaQueryWrapper<KnowledgeCandidate>().eq(KnowledgeCandidate::getOwnerId, ownerId);
    // 分页条件（PENDING 优先，其余按创建时间倒序）
    LambdaQueryWrapper<KnowledgeCandidate> pageWrapper =
        new LambdaQueryWrapper<KnowledgeCandidate>()
            .eq(KnowledgeCandidate::getOwnerId, ownerId)
            .last(
                "ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, created_at DESC"
                    + " LIMIT "
                    + size
                    + " OFFSET "
                    + (page - 1) * size);
    if (status != null && !status.isBlank()) {
      countWrapper.eq(KnowledgeCandidate::getStatus, status.toUpperCase());
      pageWrapper.eq(KnowledgeCandidate::getStatus, status.toUpperCase());
    }
    long total = candidateMapper.selectCount(countWrapper);
    List<KnowledgeCandidate> records = candidateMapper.selectList(pageWrapper);
    Page<KnowledgeCandidate> result = new Page<>(page, size);
    result.setTotal(total);
    result.setRecords(records);
    return result;
  }

  @Transactional(readOnly = true)
  public KnowledgeCandidate getByIdAndOwner(Long id) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
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

  /** 编辑草稿：仅 PENDING 可编辑；覆盖草稿字段并 bump rowVersion；AI 原值保留不可变。 */
  @Transactional
  public KnowledgeCandidate updateDraft(Long id, UpdateCandidateDraftRequest request) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeCandidate current = getByIdAndOwner(id);
    if (!ExtractionConstants.CANDIDATE_PENDING.equals(current.getStatus())) {
      throw new CandidateInvalidStateException(
          "Only PENDING candidate can be edited, current status=" + current.getStatus());
    }
    requireVersion(current, request.getRowVersion());
    int updated =
        candidateMapper.update(
            null,
            new LambdaUpdateWrapper<KnowledgeCandidate>()
                .eq(KnowledgeCandidate::getId, id)
                .eq(KnowledgeCandidate::getOwnerId, ownerId)
                .eq(KnowledgeCandidate::getStatus, ExtractionConstants.CANDIDATE_PENDING)
                .eq(KnowledgeCandidate::getRowVersion, request.getRowVersion())
                .set(KnowledgeCandidate::getDraftTitle, request.getTitle().trim())
                .set(KnowledgeCandidate::getDraftSummary, request.getSummary())
                .set(KnowledgeCandidate::getDraftContent, request.getContent())
                .set(KnowledgeCandidate::getDraftKnowledgeBaseId, request.getKnowledgeBaseId())
                .set(KnowledgeCandidate::getDraftTags, joinIds(request.getTags()))
                .set(KnowledgeCandidate::getDraftUpdatedAt, Instant.now())
                .setSql("row_version = row_version + 1"));
    if (updated != 1) {
      throw new CandidateVersionConflictException(id);
    }
    return getByIdAndOwner(id);
  }

  /** 拒绝：PENDING → REJECTED；可恢复为 PENDING。 */
  @Transactional
  public KnowledgeCandidate reject(Long id, int rowVersion) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeCandidate current = getByIdAndOwner(id);
    if (!ExtractionConstants.CANDIDATE_PENDING.equals(current.getStatus())) {
      throw new CandidateInvalidStateException(
          "Only PENDING candidate can be rejected, current status=" + current.getStatus());
    }
    requireVersion(current, rowVersion);
    int updated =
        candidateMapper.update(
            null,
            new LambdaUpdateWrapper<KnowledgeCandidate>()
                .eq(KnowledgeCandidate::getId, id)
                .eq(KnowledgeCandidate::getOwnerId, ownerId)
                .eq(KnowledgeCandidate::getStatus, ExtractionConstants.CANDIDATE_PENDING)
                .eq(KnowledgeCandidate::getRowVersion, rowVersion)
                .set(KnowledgeCandidate::getStatus, ExtractionConstants.CANDIDATE_REJECTED)
                .set(KnowledgeCandidate::getRejectedAt, Instant.now())
                .setSql("row_version = row_version + 1"));
    if (updated != 1) {
      throw new CandidateVersionConflictException(id);
    }
    return getByIdAndOwner(id);
  }

  /** 撤销拒绝：REJECTED → PENDING（唯一回退）。 */
  @Transactional
  public KnowledgeCandidate restore(Long id, int rowVersion) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeCandidate current = getByIdAndOwner(id);
    if (!ExtractionConstants.CANDIDATE_REJECTED.equals(current.getStatus())) {
      throw new CandidateInvalidStateException(
          "Only REJECTED candidate can be restored, current status=" + current.getStatus());
    }
    requireVersion(current, rowVersion);
    int updated =
        candidateMapper.update(
            null,
            new LambdaUpdateWrapper<KnowledgeCandidate>()
                .eq(KnowledgeCandidate::getId, id)
                .eq(KnowledgeCandidate::getOwnerId, ownerId)
                .eq(KnowledgeCandidate::getStatus, ExtractionConstants.CANDIDATE_REJECTED)
                .eq(KnowledgeCandidate::getRowVersion, rowVersion)
                .set(KnowledgeCandidate::getStatus, ExtractionConstants.CANDIDATE_PENDING)
                .set(KnowledgeCandidate::getRejectedAt, null)
                .setSql("row_version = row_version + 1"));
    if (updated != 1) {
      throw new CandidateVersionConflictException(id);
    }
    return getByIdAndOwner(id);
  }

  private static void requireVersion(KnowledgeCandidate candidate, int rowVersion) {
    if (candidate.getRowVersion() == null || candidate.getRowVersion() != rowVersion) {
      throw new CandidateVersionConflictException(candidate.getId());
    }
  }

  private static String joinIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return "";
    }
    return String.join(",", ids.stream().map(String::trim).filter(s -> !s.isBlank()).toList());
  }
}
