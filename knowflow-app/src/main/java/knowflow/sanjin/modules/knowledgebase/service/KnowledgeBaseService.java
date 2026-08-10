package knowflow.sanjin.modules.knowledgebase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseInUseException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNameConflictException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNotFoundException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseVersionConflictException;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KnowledgeBase 应用服务：Owner 隔离、名称唯一、乐观锁与软删除的事务边界。
 *
 * <p>所有按 ID 的操作先按 {@code ownerId} 过滤（越权视为不存在）；写操作通过条件更新 {@code WHERE id=? AND owner_id=? AND
 * deleted=0 AND row_version=?} 实现乐观锁，受影响行数不是 1 即视为版本冲突。名称唯一由数据库 {@code (owner_id, normalized_name,
 * deleted)} 约束兜底，并发冲突 捕获后转为业务异常。
 */
@Service
public class KnowledgeBaseService {

  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeBaseMapper mapper;
  private final JdbcTemplate jdbcTemplate;

  public KnowledgeBaseService(
      CurrentOwnerProvider currentOwnerProvider,
      KnowledgeBaseMapper mapper,
      JdbcTemplate jdbcTemplate) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 创建知识库：默认启用、未删除；normalized name 由 display name 规范化（trim + 小写）。 */
  @Transactional
  public KnowledgeBase create(CreateKnowledgeBaseRequest request) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    String normalizedName = normalizeName(request.getName());

    KnowledgeBase kb = new KnowledgeBase();
    kb.setOwnerId(ownerId);
    kb.setDisplayName(request.getName().trim());
    kb.setNormalizedName(normalizedName);
    kb.setDescription(request.getDescription());
    kb.setEnabled(true);
    kb.setDeleted(false);
    kb.setRowVersion(0);

    try {
      mapper.insert(kb);
    } catch (DuplicateKeyException e) {
      throw new KnowledgeBaseNameConflictException(request.getName().trim());
    }
    return kb;
  }

  @Transactional(readOnly = true)
  public List<KnowledgeBase> listForOwner() {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return mapper.selectList(
        new LambdaQueryWrapper<KnowledgeBase>()
            .eq(KnowledgeBase::getOwnerId, ownerId)
            .eq(KnowledgeBase::getDeleted, false)
            .orderByDesc(KnowledgeBase::getCreatedAt));
  }

  @Transactional(readOnly = true)
  public KnowledgeBase getByIdAndOwner(Long id) {
    return getByIdAndOwnerInternal(id);
  }

  /** 更新：校验版本后按条件更新，字段未传则保持不变；名称变更会同步 normalized name。 */
  @Transactional
  public KnowledgeBase update(Long id, UpdateKnowledgeBaseRequest request) {
    KnowledgeBase kb = getByIdAndOwnerInternal(id);
    if (request.getRowVersion() == null || !request.getRowVersion().equals(kb.getRowVersion())) {
      throw new KnowledgeBaseVersionConflictException();
    }

    LambdaUpdateWrapper<KnowledgeBase> update = versionedUpdate(id, request.getRowVersion());
    if (request.getName() != null) {
      String normalizedName = normalizeName(request.getName());
      update
          .set(KnowledgeBase::getDisplayName, request.getName().trim())
          .set(KnowledgeBase::getNormalizedName, normalizedName);
    }
    if (request.getDescription() != null) {
      update.set(KnowledgeBase::getDescription, request.getDescription());
    }
    if (request.getEnabled() != null) {
      update.set(KnowledgeBase::getEnabled, request.getEnabled());
    }
    update.setSql("row_version = row_version + 1");

    try {
      requireVersionedWrite(mapper.update(null, update));
    } catch (DuplicateKeyException e) {
      throw new KnowledgeBaseNameConflictException(
          request.getName() != null ? request.getName().trim() : kb.getDisplayName());
    }
    return getByIdAndOwnerInternal(id);
  }

  /** 软删除：置 deleted=true 并递增版本，返回新版本号（供前端更新本地状态）。 */
  @Transactional
  public int softDelete(Long id, int rowVersion) {
    KnowledgeBase kb = getByIdAndOwnerInternal(id);
    requireExpectedVersion(kb, rowVersion);
    ensureNoOrphanedItems(id);
    LambdaUpdateWrapper<KnowledgeBase> update = versionedUpdate(id, rowVersion);
    update.set(KnowledgeBase::getDeleted, true).setSql("row_version = row_version + 1");
    requireVersionedWrite(mapper.update(null, update));
    return rowVersion + 1;
  }

  /**
   * 归属约束（DECISIONS §10）：若存在只归属于本 KB 的活跃 KnowledgeItem，删除会导致其零归属，阻止删除。 通过 JdbcTemplate 直查关联表，避免
   * knowledgebase ↔ knowledge 模块反向依赖。
   */
  private void ensureNoOrphanedItems(Long knowledgeBaseId) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM knowledge_base_item kbi "
                + "WHERE kbi.knowledge_base_id = ? AND kbi.deleted = 0 "
                + "AND NOT EXISTS (SELECT 1 FROM knowledge_base_item other "
                + "  WHERE other.knowledge_item_id = kbi.knowledge_item_id "
                + "    AND other.knowledge_base_id <> ? AND other.deleted = 0)",
            Long.class,
            knowledgeBaseId,
            knowledgeBaseId);
    if (count != null && count > 0) {
      throw new KnowledgeBaseInUseException(knowledgeBaseId);
    }
  }

  @Transactional
  public int disable(Long id, int rowVersion) {
    return setEnabled(id, rowVersion, false);
  }

  @Transactional
  public int enable(Long id, int rowVersion) {
    return setEnabled(id, rowVersion, true);
  }

  /** 启用/禁用：目标状态一致时幂等返回原版本，否则条件更新并递增版本。 */
  private int setEnabled(Long id, int rowVersion, boolean enabled) {
    KnowledgeBase kb = getByIdAndOwnerInternal(id);
    requireExpectedVersion(kb, rowVersion);
    if (kb.getEnabled() != null && kb.getEnabled() == enabled) {
      return rowVersion;
    }
    LambdaUpdateWrapper<KnowledgeBase> update = versionedUpdate(id, rowVersion);
    update.set(KnowledgeBase::getEnabled, enabled).setSql("row_version = row_version + 1");
    requireVersionedWrite(mapper.update(null, update));
    return rowVersion + 1;
  }

  private KnowledgeBase getByIdAndOwnerInternal(Long id) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeBase kb =
        mapper.selectOne(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getOwnerId, ownerId)
                .eq(KnowledgeBase::getDeleted, false));
    if (kb == null) {
      throw new KnowledgeBaseNotFoundException(id);
    }
    return kb;
  }

  private LambdaUpdateWrapper<KnowledgeBase> versionedUpdate(Long id, int rowVersion) {
    return new LambdaUpdateWrapper<KnowledgeBase>()
        .eq(KnowledgeBase::getId, id)
        .eq(KnowledgeBase::getOwnerId, currentOwnerProvider.getCurrentOwnerId())
        .eq(KnowledgeBase::getDeleted, false)
        .eq(KnowledgeBase::getRowVersion, rowVersion);
  }

  private static void requireExpectedVersion(KnowledgeBase kb, int rowVersion) {
    if (kb.getRowVersion() == null || kb.getRowVersion() != rowVersion) {
      throw new KnowledgeBaseVersionConflictException();
    }
  }

  private static void requireVersionedWrite(int affectedRows) {
    if (affectedRows != 1) {
      throw new KnowledgeBaseVersionConflictException();
    }
  }

  private static String normalizeName(String name) {
    return name.trim().toLowerCase();
  }
}
