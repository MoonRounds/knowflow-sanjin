package knowflow.sanjin.modules.knowledgebase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNameConflictException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseNotFoundException;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseVersionConflictException;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseService {

  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeBaseMapper mapper;

  public KnowledgeBaseService(
      CurrentOwnerProvider currentOwnerProvider, KnowledgeBaseMapper mapper) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.mapper = mapper;
  }

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

  @Transactional
  public int softDelete(Long id, int rowVersion) {
    KnowledgeBase kb = getByIdAndOwnerInternal(id);
    requireExpectedVersion(kb, rowVersion);
    LambdaUpdateWrapper<KnowledgeBase> update = versionedUpdate(id, rowVersion);
    update.set(KnowledgeBase::getDeleted, true).setSql("row_version = row_version + 1");
    requireVersionedWrite(mapper.update(null, update));
    return rowVersion + 1;
  }

  @Transactional
  public int disable(Long id, int rowVersion) {
    return setEnabled(id, rowVersion, false);
  }

  @Transactional
  public int enable(Long id, int rowVersion) {
    return setEnabled(id, rowVersion, true);
  }

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
