package knowflow.sanjin.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.dto.CreateDocumentRequest;
import knowflow.sanjin.modules.knowledge.dto.UpdateDocumentRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocumentTag;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeDocumentNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeDocumentVersionConflictException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeIndexTaskConflictException;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentTagMapper;
import knowflow.sanjin.modules.knowledge.mapper.TagMapper;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual Note 应用服务：Owner 隔离、单归属 KB 与 Tag 关联、乐观锁与索引任务提交的事务边界。
 *
 * <p>编辑区分两类索引任务（DECISIONS §12）：内容/标题变化走 FULL 索引（contentVersion+1，重新 Chunk/Embedding）； 仅
 * KnowledgeBase 归属/Tags 变化走 PAYLOAD 更新（不 bump 版本，只更新 Qdrant metadata）。仅 summary 变化 不产生任务。Tag 关联写入采用
 * 「按 pair 恢复」模式：存在即恢复 deleted=0，不存在才插入，与复合唯一约束兼容。KB 为单归属（ADR 0007），直接落在 {@code
 * knowledge_document.kb_id}。
 */
@Service
public class KnowledgeDocumentService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

  private static final int DEFAULT_MAX_RETRIES = 3;

  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeDocumentMapper documentMapper;
  private final KnowledgeDocumentTagMapper documentTagMapper;
  private final TagMapper tagMapper;
  private final KnowledgeBaseService knowledgeBaseService;
  private final TaskSubmissionService taskSubmissionService;
  private final List<KnowledgeDocumentLifecycleHandler> lifecycleHandlers;

  public KnowledgeDocumentService(
      CurrentOwnerProvider currentOwnerProvider,
      KnowledgeDocumentMapper documentMapper,
      KnowledgeDocumentTagMapper documentTagMapper,
      TagMapper tagMapper,
      KnowledgeBaseService knowledgeBaseService,
      TaskSubmissionService taskSubmissionService,
      List<KnowledgeDocumentLifecycleHandler> lifecycleHandlers) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.documentMapper = documentMapper;
    this.documentTagMapper = documentTagMapper;
    this.tagMapper = tagMapper;
    this.knowledgeBaseService = knowledgeBaseService;
    this.taskSubmissionService = taskSubmissionService;
    this.lifecycleHandlers = lifecycleHandlers;
  }

  @Transactional
  public KnowledgeDocument createManualNote(CreateDocumentRequest request) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();

    Long kbId = resolveKnowledgeBaseId(ownerId, request.getKnowledgeBaseId());
    String title =
        request.getTitle() != null && !request.getTitle().isBlank()
            ? request.getTitle().trim()
            : deriveTitleFromContent(request.getContent());
    List<String> tagNames = normalizeTags(request.getTags());

    KnowledgeDocument document = new KnowledgeDocument();
    document.setOwnerId(ownerId);
    document.setKbId(kbId);
    document.setSourceType(KnowledgeConstants.SOURCE_MANUAL_NOTE);
    document.setTitle(title);
    document.setSummary(request.getSummary());
    document.setContent(request.getContent());
    document.setContentVersion(1);
    document.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    document.setDeleted(false);
    document.setRowVersion(0);
    documentMapper.insert(document);

    replaceTagRelations(ownerId, document.getId(), tagNames);

    submitIndexTask(document.getId(), ownerId, 1);
    return document;
  }

  /** 创建 Upload 来源 Document：正文占位由解析阶段填充；kbId 由调用方校验后直接设置，不在此提交索引任务（解析成功后才触发）。 */
  @Transactional
  public KnowledgeDocument createUploadItem(Long kbId, String title) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();

    KnowledgeDocument document = new KnowledgeDocument();
    document.setOwnerId(ownerId);
    document.setKbId(kbId);
    document.setSourceType(KnowledgeConstants.SOURCE_UPLOAD_FILE);
    document.setTitle(title);
    document.setSummary(null);
    document.setContent("");
    document.setContentVersion(1);
    document.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    document.setDeleted(false);
    document.setRowVersion(0);
    documentMapper.insert(document);
    return document;
  }

  /**
   * 恢复软删的 Upload Document：重置单归属 KB、清删除标记、重置正文占位与索引状态，供解析阶段填充。
   *
   * <p>恢复必须进入新的 contentVersion。删除任务只清理删除当时及以前的版本；若恢复复用旧版本号，迟到的删除任务会把恢复后新建的 Qdrant Point 一并删除。
   */
  @Transactional
  public KnowledgeDocument restoreUploadItem(Long itemId, Long kbId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeDocument document =
        documentMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, itemId)
                .eq(KnowledgeDocument::getOwnerId, ownerId));
    if (document == null) {
      throw new KnowledgeDocumentNotFoundException(itemId);
    }
    document.setKbId(kbId);
    document.setDeleted(false);
    document.setContent("");
    document.setContentVersion(
        document.getContentVersion() == null ? 1 : document.getContentVersion() + 1);
    document.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    document.setIndexErrorCode(null);
    document.setIndexErrorMessage(null);
    documentMapper.updateById(document);
    return document;
  }

  @Transactional(readOnly = true)
  public List<KnowledgeDocument> listForOwner() {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return documentMapper.selectList(
        new LambdaQueryWrapper<KnowledgeDocument>()
            .eq(KnowledgeDocument::getOwnerId, ownerId)
            .eq(KnowledgeDocument::getDeleted, false)
            .orderByDesc(KnowledgeDocument::getCreatedAt));
  }

  @Transactional(readOnly = true)
  public KnowledgeDocument getByIdAndOwner(Long id) {
    return getByIdAndOwnerInternal(id);
  }

  /**
   * 按 owner 查询 Document，包含软删行；供 Upload 去重恢复路径在恢复前判断软删状态。
   *
   * <p>owner 越权与不存在均抛 {@link KnowledgeDocumentNotFoundException}。
   */
  @Transactional(readOnly = true)
  public KnowledgeDocument getByIdAndOwnerIncludingDeleted(Long id) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeDocument document =
        documentMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, id)
                .eq(KnowledgeDocument::getOwnerId, ownerId));
    if (document == null) {
      throw new KnowledgeDocumentNotFoundException(id);
    }
    return document;
  }

  /** 返回 Document 当前归属的 KnowledgeBase id（owner 过滤，单归属）。 */
  @Transactional(readOnly = true)
  public Long getKnowledgeBaseId(Long itemId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeDocument document =
        documentMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, itemId)
                .eq(KnowledgeDocument::getOwnerId, ownerId));
    if (document == null) {
      throw new KnowledgeDocumentNotFoundException(itemId);
    }
    return document.getKbId();
  }

  /** 返回 Document 当前活跃的 Tag 名称列表（owner 过滤）。 */
  @Transactional(readOnly = true)
  public List<String> getTagNames(Long itemId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return listActiveTagIds(ownerId, itemId).stream()
        .map(tagMapper::selectById)
        .filter(Objects::nonNull)
        .map(Tag::getNormalizedName)
        .toList();
  }

  /** 批量返回 documentId → 归属 KnowledgeBase id（owner 过滤，单归属）。 */
  @Transactional(readOnly = true)
  public Map<Long, Long> batchKnowledgeBaseId(List<Long> itemIds) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    return documentMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getOwnerId, ownerId)
                .in(KnowledgeDocument::getId, itemIds)
                .eq(KnowledgeDocument::getDeleted, false))
        .stream()
        .collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getKbId));
  }

  /** 批量返回 documentId → 活跃 Tag 名称列表（owner 过滤）。 */
  @Transactional(readOnly = true)
  public Map<Long, List<String>> batchTagNames(List<Long> itemIds) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    List<Long> tagIds =
        documentTagMapper
            .selectList(
                new LambdaQueryWrapper<KnowledgeDocumentTag>()
                    .eq(KnowledgeDocumentTag::getOwnerId, ownerId)
                    .in(KnowledgeDocumentTag::getKnowledgeDocumentId, itemIds)
                    .eq(KnowledgeDocumentTag::getDeleted, false))
            .stream()
            .map(KnowledgeDocumentTag::getTagId)
            .distinct()
            .toList();
    if (tagIds.isEmpty()) {
      return itemIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
    }
    Map<Long, String> tagIdToName =
        tagMapper.selectBatchIds(tagIds).stream()
            .collect(Collectors.toMap(Tag::getId, Tag::getNormalizedName));
    return documentTagMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeDocumentTag>()
                .eq(KnowledgeDocumentTag::getOwnerId, ownerId)
                .in(KnowledgeDocumentTag::getKnowledgeDocumentId, itemIds)
                .eq(KnowledgeDocumentTag::getDeleted, false))
        .stream()
        .collect(
            Collectors.groupingBy(
                KnowledgeDocumentTag::getKnowledgeDocumentId,
                Collectors.mapping(
                    rel -> tagIdToName.getOrDefault(rel.getTagId(), ""), Collectors.toList())));
  }

  /** 编辑：内容/标题变化触发新版本 FULL 索引；仅 KB 归属/Tags 变化触发 PAYLOAD 更新；仅 summary 变化不建任务。 */
  @Transactional
  public KnowledgeDocument updateManualNote(Long id, UpdateDocumentRequest request) {
    KnowledgeDocument current = getByIdAndOwnerInternal(id);
    requireExpectedVersion(current, request.getRowVersion());
    long ownerId = current.getOwnerId();

    Long resolvedKbId = resolveKnowledgeBaseId(ownerId, request.getKnowledgeBaseId());
    List<String> tagNames = normalizeTags(request.getTags());

    String newTitle = request.getTitle() != null ? request.getTitle().trim() : current.getTitle();
    String newContent = request.getContent() != null ? request.getContent() : current.getContent();
    String newSummary = request.getSummary() != null ? request.getSummary() : current.getSummary();

    boolean contentChanged = !Objects.equals(current.getContent(), newContent);
    boolean titleChanged = !Objects.equals(current.getTitle(), newTitle);
    boolean kbChanged = !Objects.equals(current.getKbId(), resolvedKbId);
    boolean tagsChanged = tagRelationsChanged(ownerId, id, tagNames);
    boolean summaryChanged = !Objects.equals(current.getSummary(), newSummary);

    if (!contentChanged && !titleChanged && !kbChanged && !tagsChanged && !summaryChanged) {
      return current;
    }

    LambdaUpdateWrapper<KnowledgeDocument> update =
        new LambdaUpdateWrapper<KnowledgeDocument>()
            .eq(KnowledgeDocument::getId, id)
            .eq(KnowledgeDocument::getOwnerId, ownerId)
            .eq(KnowledgeDocument::getDeleted, false)
            .eq(KnowledgeDocument::getRowVersion, request.getRowVersion())
            .set(KnowledgeDocument::getTitle, newTitle)
            .set(KnowledgeDocument::getContent, newContent)
            .set(KnowledgeDocument::getSummary, newSummary)
            .setSql("row_version = row_version + 1");
    if (kbChanged) {
      update.set(KnowledgeDocument::getKbId, resolvedKbId);
    }
    if (contentChanged || titleChanged) {
      update
          .set(KnowledgeDocument::getContentVersion, current.getContentVersion() + 1)
          .set(KnowledgeDocument::getIndexStatus, KnowledgeConstants.INDEX_PENDING)
          .set(KnowledgeDocument::getIndexErrorCode, null)
          .set(KnowledgeDocument::getIndexErrorMessage, null);
    }
    if (documentMapper.update(null, update) != 1) {
      throw new KnowledgeDocumentVersionConflictException();
    }

    if (tagsChanged) {
      replaceTagRelations(ownerId, id, tagNames);
    }

    if (contentChanged || titleChanged) {
      submitIndexTask(id, ownerId, current.getContentVersion() + 1);
    } else if (kbChanged || tagsChanged) {
      submitPayloadTask(id, ownerId, current.getContentVersion());
    }

    return getByIdAndOwnerInternal(id);
  }

  /** 软删 Document：置 deleted=true，不再可检索；创建删除任务异步清理 Qdrant Point。 */
  @Transactional
  public KnowledgeDocument softDelete(Long id, int rowVersion) {
    KnowledgeDocument current = getByIdAndOwnerInternal(id);
    requireExpectedVersion(current, rowVersion);
    long ownerId = current.getOwnerId();

    LambdaUpdateWrapper<KnowledgeDocument> update =
        new LambdaUpdateWrapper<KnowledgeDocument>()
            .eq(KnowledgeDocument::getId, id)
            .eq(KnowledgeDocument::getOwnerId, ownerId)
            .eq(KnowledgeDocument::getDeleted, false)
            .eq(KnowledgeDocument::getRowVersion, rowVersion)
            .set(KnowledgeDocument::getDeleted, true)
            .setSql("row_version = row_version + 1");
    if (documentMapper.update(null, update) != 1) {
      throw new KnowledgeDocumentVersionConflictException();
    }
    submitDeleteTask(id, ownerId, current.getContentVersion());
    notifyLifecycleHandlers(id, ownerId);
    return current;
  }

  /** 通知跨模块生命周期回调（如 Upload 原文件与 FileMetadata 清理），失败不阻塞主流程。 */
  private void notifyLifecycleHandlers(Long itemId, long ownerId) {
    for (KnowledgeDocumentLifecycleHandler handler : lifecycleHandlers) {
      try {
        handler.onDocumentSoftDeleted(itemId, ownerId);
      } catch (RuntimeException e) {
        log.warn("Document {} 生命周期回调失败：{}", itemId, e.getMessage());
      }
    }
  }

  private void submitDeleteTask(Long itemId, long ownerId, int deleteThroughVersion) {
    try {
      taskSubmissionService.submit(
          ProcessingConstants.TASK_TYPE_KNOWLEDGE_DELETE,
          businessKey(itemId, deleteThroughVersion) + KnowledgeConstants.BUSINESS_KEY_DELETE_SUFFIX,
          itemId,
          ownerId,
          null,
          DEFAULT_MAX_RETRIES);
    } catch (DuplicateKeyException e) {
      // 已有活动删除任务，跳过
    }
  }

  private KnowledgeDocument getByIdAndOwnerInternal(Long id) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeDocument document =
        documentMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, id)
                .eq(KnowledgeDocument::getOwnerId, ownerId)
                .eq(KnowledgeDocument::getDeleted, false));
    if (document == null) {
      throw new KnowledgeDocumentNotFoundException(id);
    }
    return document;
  }

  private static void requireExpectedVersion(KnowledgeDocument document, int rowVersion) {
    if (document.getRowVersion() == null || document.getRowVersion() != rowVersion) {
      throw new KnowledgeDocumentVersionConflictException();
    }
  }

  /** 单归属 KB 解析：校验原始 id 非空且知识库存在并属于当前 owner，返回 Long。 */
  private Long resolveKnowledgeBaseId(long ownerId, String rawId) {
    if (rawId == null || rawId.isBlank()) {
      throw new IllegalArgumentException("At least one KnowledgeBase must be associated");
    }
    Long id = Long.valueOf(rawId);
    knowledgeBaseService.getByIdAndOwner(id); // 校验存在与 owner 边界
    return id;
  }

  private void submitIndexTask(Long itemId, long ownerId, int contentVersion) {
    try {
      taskSubmissionService.submit(
          ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX,
          businessKey(itemId, contentVersion),
          itemId,
          ownerId,
          null,
          DEFAULT_MAX_RETRIES);
    } catch (DuplicateKeyException e) {
      throw new KnowledgeIndexTaskConflictException(itemId, contentVersion);
    }
  }

  private void submitPayloadTask(Long itemId, long ownerId, int contentVersion) {
    try {
      taskSubmissionService.submit(
          ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX,
          businessKey(itemId, contentVersion) + KnowledgeConstants.BUSINESS_KEY_PAYLOAD_SUFFIX,
          itemId,
          ownerId,
          null,
          DEFAULT_MAX_RETRIES);
    } catch (DuplicateKeyException e) {
      // 已有活动 PAYLOAD 任务，跳过（同版本内容未变，payload 幂等收敛）
    }
  }

  /**
   * 解析成功后提交索引任务（Upload 流程复用）：已有活动索引任务时跳过（幂等），不抛冲突。
   *
   * <p>与 {@link #submitIndexTask} 不同：解析场景下重复消费/重试时若索引任务已存在，直接跳过即可。
   */
  public void submitIndexTaskAfterParse(Long itemId, long ownerId, int contentVersion) {
    try {
      taskSubmissionService.submit(
          ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX,
          businessKey(itemId, contentVersion),
          itemId,
          ownerId,
          null,
          DEFAULT_MAX_RETRIES);
    } catch (DuplicateKeyException e) {
      // 已有活动索引任务，跳过
    }
  }

  /**
   * 全量重建（G13）：对当前 Owner 未软删且「缺索引或索引过期」的 Document 按当前 contentVersion 提交 FULL 索引任务。
   *
   * <p>用于存量回填 / Qdrant 全量重建 / Embedding 维度变更后的重建（DECISIONS §12）。已 INDEXED 且 {@code indexed_version
   * == content_version} 的 Document 跳过，避免无谓重索引。幂等：已有活动索引任务的 Document 跳过（{@code knowledge_document}
   * 同版本点会被确定性 Point ID 覆盖写入，无需清理）。
   */
  @Transactional
  public int reindexAllForOwner() {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    List<KnowledgeDocument> documents =
        documentMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getOwnerId, ownerId)
                .eq(KnowledgeDocument::getDeleted, false));
    int submitted = 0;
    for (KnowledgeDocument document : documents) {
      if (isIndexCurrent(document)) {
        continue;
      }
      int version = document.getContentVersion() == null ? 1 : document.getContentVersion();
      submitIndexTaskAfterParse(document.getId(), ownerId, version);
      submitted++;
    }
    log.info("reindexAllForOwner: owner={} submitted {} documents", ownerId, submitted);
    return submitted;
  }

  private boolean isIndexCurrent(KnowledgeDocument document) {
    if (!KnowledgeConstants.INDEX_INDEXED.equals(document.getIndexStatus())) {
      return false;
    }
    Integer contentVersion = document.getContentVersion();
    if (contentVersion == null) {
      return false;
    }
    return Integer.valueOf(contentVersion).equals(document.getIndexedVersion());
  }

  private static String businessKey(Long itemId, int contentVersion) {
    return KnowledgeConstants.BUSINESS_KEY_PREFIX
        + itemId
        + KnowledgeConstants.BUSINESS_KEY_DELIMITER
        + contentVersion;
  }

  private boolean tagRelationsChanged(long ownerId, Long itemId, List<String> newNames) {
    Set<String> current =
        listActiveTagIds(ownerId, itemId).stream()
            .map(tagMapper::selectById)
            .map(Tag::getNormalizedName)
            .collect(Collectors.toSet());
    Set<String> expected = new HashSet<>(newNames);
    return !current.equals(expected);
  }

  private List<Long> listActiveTagIds(long ownerId, Long itemId) {
    return documentTagMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeDocumentTag>()
                .eq(KnowledgeDocumentTag::getOwnerId, ownerId)
                .eq(KnowledgeDocumentTag::getKnowledgeDocumentId, itemId)
                .eq(KnowledgeDocumentTag::getDeleted, false))
        .stream()
        .map(KnowledgeDocumentTag::getTagId)
        .toList();
  }

  private void replaceTagRelations(long ownerId, Long itemId, List<String> newNames) {
    List<KnowledgeDocumentTag> existing =
        documentTagMapper.selectList(
            new LambdaQueryWrapper<KnowledgeDocumentTag>()
                .eq(KnowledgeDocumentTag::getOwnerId, ownerId)
                .eq(KnowledgeDocumentTag::getKnowledgeDocumentId, itemId));
    Set<Long> keepTagIds = new HashSet<>();
    for (String name : newNames) {
      Long tagId = findOrCreateTag(ownerId, name);
      keepTagIds.add(tagId);
    }

    for (KnowledgeDocumentTag rel : existing) {
      if (!keepTagIds.contains(rel.getTagId()) && !rel.getDeleted()) {
        rel.setDeleted(true);
        documentTagMapper.updateById(rel);
      }
    }

    // 需要建立关联的 tag：没有活跃关联则插入或恢复
    List<Long> activeTagIds = listActiveTagIds(ownerId, itemId);
    for (Long tagId : keepTagIds) {
      if (activeTagIds.contains(tagId)) {
        continue;
      }
      KnowledgeDocumentTag stale =
          existing.stream().filter(r -> r.getTagId().equals(tagId)).findFirst().orElse(null);
      if (stale != null) {
        stale.setDeleted(false);
        documentTagMapper.updateById(stale);
      } else {
        KnowledgeDocumentTag fresh = new KnowledgeDocumentTag();
        fresh.setOwnerId(ownerId);
        fresh.setKnowledgeDocumentId(itemId);
        fresh.setTagId(tagId);
        fresh.setDeleted(false);
        documentTagMapper.insert(fresh);
      }
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

  private static List<String> normalizeTags(List<String> tags) {
    if (tags == null) {
      return List.of();
    }
    return tags.stream()
        .filter(t -> t != null && !t.isBlank())
        .map(String::trim)
        .map(String::toLowerCase)
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .toList();
  }

  private static String deriveTitleFromContent(String content) {
    String firstLine = content.stripLeading().lines().findFirst().orElse("").trim();
    if (firstLine.isBlank()) {
      return "Untitled Note";
    }
    return firstLine.length() <= 200 ? firstLine : firstLine.substring(0, 200);
  }
}
