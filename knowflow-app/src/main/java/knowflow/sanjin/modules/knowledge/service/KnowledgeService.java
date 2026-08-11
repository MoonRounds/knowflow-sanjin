package knowflow.sanjin.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.dto.CreateManualNoteRequest;
import knowflow.sanjin.modules.knowledge.dto.UpdateManualNoteRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeBaseItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItemTag;
import knowflow.sanjin.modules.knowledge.entity.Tag;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeIndexTaskConflictException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeItemNotFoundException;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeItemVersionConflictException;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeBaseItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemTagMapper;
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
 * Manual Note 应用服务：Owner 隔离、KB/Tag 关联、乐观锁与索引任务提交的事务边界。
 *
 * <p>编辑区分两类索引任务（DECISIONS §12）：内容/标题变化走 FULL 索引（contentVersion+1，重新 Chunk/Embedding）； 仅
 * KnowledgeBase 关系/Tags 变化走 PAYLOAD 更新（不 bump 版本，只更新 Qdrant metadata）。仅 summary 变化 不产生任务。关联写入采用「按
 * pair 恢复」模式：存在即恢复 deleted=0，不存在才插入，与复合唯一约束兼容。
 */
@Service
public class KnowledgeService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

  private static final int DEFAULT_MAX_RETRIES = 3;

  private final CurrentOwnerProvider currentOwnerProvider;
  private final KnowledgeItemMapper itemMapper;
  private final KnowledgeBaseItemMapper kbItemMapper;
  private final KnowledgeItemTagMapper itemTagMapper;
  private final TagMapper tagMapper;
  private final KnowledgeBaseService knowledgeBaseService;
  private final TaskSubmissionService taskSubmissionService;
  private final List<KnowledgeItemLifecycleHandler> lifecycleHandlers;

  public KnowledgeService(
      CurrentOwnerProvider currentOwnerProvider,
      KnowledgeItemMapper itemMapper,
      KnowledgeBaseItemMapper kbItemMapper,
      KnowledgeItemTagMapper itemTagMapper,
      TagMapper tagMapper,
      KnowledgeBaseService knowledgeBaseService,
      TaskSubmissionService taskSubmissionService,
      List<KnowledgeItemLifecycleHandler> lifecycleHandlers) {
    this.currentOwnerProvider = currentOwnerProvider;
    this.itemMapper = itemMapper;
    this.kbItemMapper = kbItemMapper;
    this.itemTagMapper = itemTagMapper;
    this.tagMapper = tagMapper;
    this.knowledgeBaseService = knowledgeBaseService;
    this.taskSubmissionService = taskSubmissionService;
    this.lifecycleHandlers = lifecycleHandlers;
  }

  @Transactional
  public KnowledgeItem createManualNote(CreateManualNoteRequest request) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();

    List<Long> kbIds = resolveKnowledgeBaseIds(ownerId, request.getKnowledgeBaseIds());
    String title =
        request.getTitle() != null && !request.getTitle().isBlank()
            ? request.getTitle().trim()
            : deriveTitleFromContent(request.getContent());
    List<String> tagNames = normalizeTags(request.getTags());

    KnowledgeItem item = new KnowledgeItem();
    item.setOwnerId(ownerId);
    item.setSourceType(KnowledgeConstants.SOURCE_MANUAL_NOTE);
    item.setTitle(title);
    item.setSummary(request.getSummary());
    item.setContent(request.getContent());
    item.setContentVersion(1);
    item.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    item.setStatus(KnowledgeConstants.STATUS_ACTIVE);
    item.setRowVersion(0);
    itemMapper.insert(item);

    replaceKnowledgeBaseRelations(ownerId, item.getId(), kbIds);
    replaceTagRelations(ownerId, item.getId(), tagNames);

    submitIndexTask(item.getId(), ownerId, 1);
    return item;
  }

  /** 创建 Upload 来源 Item：正文占位由解析阶段填充；只关联 KnowledgeBase，不在此提交索引任务（解析成功后才触发）。 */
  @Transactional
  public KnowledgeItem createUploadItem(List<Long> kbIds, String title) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    List<Long> resolved = resolveLongKnowledgeBaseIds(ownerId, kbIds);

    KnowledgeItem item = new KnowledgeItem();
    item.setOwnerId(ownerId);
    item.setSourceType(KnowledgeConstants.SOURCE_UPLOAD_FILE);
    item.setTitle(title);
    item.setSummary(null);
    item.setContent("");
    item.setContentVersion(1);
    item.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    item.setStatus(KnowledgeConstants.STATUS_ACTIVE);
    item.setRowVersion(0);
    itemMapper.insert(item);

    replaceKnowledgeBaseRelations(ownerId, item.getId(), resolved);
    return item;
  }

  /** 恢复软删的 Upload Item：重新关联 KB、重置正文占位与索引状态，供解析阶段填充。 */
  @Transactional
  public KnowledgeItem restoreUploadItem(Long itemId, List<Long> kbIds) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeItem item =
        itemMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeItem>()
                .eq(KnowledgeItem::getId, itemId)
                .eq(KnowledgeItem::getOwnerId, ownerId));
    if (item == null) {
      throw new KnowledgeItemNotFoundException(itemId);
    }
    List<Long> resolved = resolveLongKnowledgeBaseIds(ownerId, kbIds);
    item.setStatus(KnowledgeConstants.STATUS_ACTIVE);
    item.setContent("");
    item.setContentVersion(1);
    item.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    item.setIndexErrorCode(null);
    item.setIndexErrorMessage(null);
    itemMapper.updateById(item);
    replaceKnowledgeBaseRelations(ownerId, itemId, resolved);
    return item;
  }

  @Transactional(readOnly = true)
  public List<KnowledgeItem> listForOwner() {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return itemMapper.selectList(
        new LambdaQueryWrapper<KnowledgeItem>()
            .eq(KnowledgeItem::getOwnerId, ownerId)
            .eq(KnowledgeItem::getStatus, KnowledgeConstants.STATUS_ACTIVE)
            .orderByDesc(KnowledgeItem::getCreatedAt));
  }

  @Transactional(readOnly = true)
  public KnowledgeItem getByIdAndOwner(Long id) {
    return getByIdAndOwnerInternal(id);
  }

  /** 返回 Item 当前活跃的 KnowledgeBase id（owner 过滤）。 */
  @Transactional(readOnly = true)
  public List<Long> getKnowledgeBaseIds(Long itemId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return listActiveKbIds(ownerId, itemId);
  }

  /** 返回 Item 当前活跃的 Tag 名称列表（owner 过滤）。 */
  @Transactional(readOnly = true)
  public List<String> getTagNames(Long itemId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return listActiveTagIds(ownerId, itemId).stream()
        .map(tagMapper::selectById)
        .filter(Objects::nonNull)
        .map(Tag::getNormalizedName)
        .toList();
  }

  /** 批量返回 itemId → 活跃 KnowledgeBase id 列表（owner 过滤）。 */
  @Transactional(readOnly = true)
  public Map<Long, List<Long>> batchKnowledgeBaseIds(List<Long> itemIds) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    return kbItemMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeBaseItem>()
                .eq(KnowledgeBaseItem::getOwnerId, ownerId)
                .in(KnowledgeBaseItem::getKnowledgeItemId, itemIds)
                .eq(KnowledgeBaseItem::getDeleted, false))
        .stream()
        .collect(
            Collectors.groupingBy(
                KnowledgeBaseItem::getKnowledgeItemId,
                Collectors.mapping(KnowledgeBaseItem::getKnowledgeBaseId, Collectors.toList())));
  }

  /** 批量返回 itemId → 活跃 Tag 名称列表（owner 过滤）。 */
  @Transactional(readOnly = true)
  public Map<Long, List<String>> batchTagNames(List<Long> itemIds) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    if (itemIds.isEmpty()) {
      return Map.of();
    }
    List<Long> tagIds =
        itemTagMapper
            .selectList(
                new LambdaQueryWrapper<KnowledgeItemTag>()
                    .eq(KnowledgeItemTag::getOwnerId, ownerId)
                    .in(KnowledgeItemTag::getKnowledgeItemId, itemIds)
                    .eq(KnowledgeItemTag::getDeleted, false))
            .stream()
            .map(KnowledgeItemTag::getTagId)
            .distinct()
            .toList();
    if (tagIds.isEmpty()) {
      return itemIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
    }
    Map<Long, String> tagIdToName =
        tagMapper.selectBatchIds(tagIds).stream()
            .collect(Collectors.toMap(Tag::getId, Tag::getNormalizedName));
    return itemTagMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeItemTag>()
                .eq(KnowledgeItemTag::getOwnerId, ownerId)
                .in(KnowledgeItemTag::getKnowledgeItemId, itemIds)
                .eq(KnowledgeItemTag::getDeleted, false))
        .stream()
        .collect(
            Collectors.groupingBy(
                KnowledgeItemTag::getKnowledgeItemId,
                Collectors.mapping(
                    rel -> tagIdToName.getOrDefault(rel.getTagId(), ""), Collectors.toList())));
  }

  private List<Long> listActiveKbIds(long ownerId, Long itemId) {
    return kbItemMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeBaseItem>()
                .eq(KnowledgeBaseItem::getOwnerId, ownerId)
                .eq(KnowledgeBaseItem::getKnowledgeItemId, itemId)
                .eq(KnowledgeBaseItem::getDeleted, false))
        .stream()
        .map(KnowledgeBaseItem::getKnowledgeBaseId)
        .toList();
  }

  /** 编辑：内容/标题变化触发新版本 FULL 索引；仅关系/Tags 变化触发 PAYLOAD 更新；仅 summary 变化不建任务。 */
  @Transactional
  public KnowledgeItem updateManualNote(Long id, UpdateManualNoteRequest request) {
    KnowledgeItem current = getByIdAndOwnerInternal(id);
    requireExpectedVersion(current, request.getRowVersion());
    long ownerId = current.getOwnerId();

    List<Long> kbIds = resolveKnowledgeBaseIds(ownerId, request.getKnowledgeBaseIds());
    List<String> tagNames = normalizeTags(request.getTags());

    String newTitle = request.getTitle() != null ? request.getTitle().trim() : current.getTitle();
    String newContent = request.getContent() != null ? request.getContent() : current.getContent();
    String newSummary = request.getSummary() != null ? request.getSummary() : current.getSummary();

    boolean contentChanged = !Objects.equals(current.getContent(), newContent);
    boolean titleChanged = !Objects.equals(current.getTitle(), newTitle);
    boolean relationsChanged = kbRelationsChanged(ownerId, id, kbIds);
    boolean tagsChanged = tagRelationsChanged(ownerId, id, tagNames);
    boolean summaryChanged = !Objects.equals(current.getSummary(), newSummary);

    if (!contentChanged && !titleChanged && !relationsChanged && !tagsChanged && !summaryChanged) {
      return current;
    }

    LambdaUpdateWrapper<KnowledgeItem> update =
        new LambdaUpdateWrapper<KnowledgeItem>()
            .eq(KnowledgeItem::getId, id)
            .eq(KnowledgeItem::getOwnerId, ownerId)
            .eq(KnowledgeItem::getStatus, KnowledgeConstants.STATUS_ACTIVE)
            .eq(KnowledgeItem::getRowVersion, request.getRowVersion())
            .set(KnowledgeItem::getTitle, newTitle)
            .set(KnowledgeItem::getContent, newContent)
            .set(KnowledgeItem::getSummary, newSummary)
            .setSql("row_version = row_version + 1");
    if (contentChanged || titleChanged) {
      update
          .set(KnowledgeItem::getContentVersion, current.getContentVersion() + 1)
          .set(KnowledgeItem::getIndexStatus, KnowledgeConstants.INDEX_PENDING)
          .set(KnowledgeItem::getIndexErrorCode, null)
          .set(KnowledgeItem::getIndexErrorMessage, null);
    }
    if (itemMapper.update(null, update) != 1) {
      throw new KnowledgeItemVersionConflictException();
    }

    if (relationsChanged) {
      replaceKnowledgeBaseRelations(ownerId, id, kbIds);
    }
    if (tagsChanged) {
      replaceTagRelations(ownerId, id, tagNames);
    }

    if (contentChanged || titleChanged) {
      submitIndexTask(id, ownerId, current.getContentVersion() + 1);
    } else if (relationsChanged || tagsChanged) {
      submitPayloadTask(id, ownerId, current.getContentVersion());
    }

    return getByIdAndOwnerInternal(id);
  }

  /** 软删 Manual Note：置 status=DELETED，不再可检索；创建删除任务异步清理 Qdrant Point。 */
  @Transactional
  public KnowledgeItem softDelete(Long id, int rowVersion) {
    KnowledgeItem current = getByIdAndOwnerInternal(id);
    requireExpectedVersion(current, rowVersion);
    long ownerId = current.getOwnerId();

    LambdaUpdateWrapper<KnowledgeItem> update =
        new LambdaUpdateWrapper<KnowledgeItem>()
            .eq(KnowledgeItem::getId, id)
            .eq(KnowledgeItem::getOwnerId, ownerId)
            .eq(KnowledgeItem::getStatus, KnowledgeConstants.STATUS_ACTIVE)
            .eq(KnowledgeItem::getRowVersion, rowVersion)
            .set(KnowledgeItem::getStatus, KnowledgeConstants.STATUS_DELETED)
            .setSql("row_version = row_version + 1");
    if (itemMapper.update(null, update) != 1) {
      throw new KnowledgeItemVersionConflictException();
    }
    // 软删关联，释放 KB 归属（避免删除 Item 触发 KB 归属约束误判）
    kbItemMapper.update(
        null,
        new LambdaUpdateWrapper<KnowledgeBaseItem>()
            .eq(KnowledgeBaseItem::getOwnerId, ownerId)
            .eq(KnowledgeBaseItem::getKnowledgeItemId, id)
            .set(KnowledgeBaseItem::getDeleted, true));
    submitDeleteTask(id, ownerId);
    notifyLifecycleHandlers(id, ownerId);
    return current;
  }

  /** 通知跨模块生命周期回调（如 Upload 原文件与 FileMetadata 清理），失败不阻塞主流程。 */
  private void notifyLifecycleHandlers(Long itemId, long ownerId) {
    for (KnowledgeItemLifecycleHandler handler : lifecycleHandlers) {
      try {
        handler.onItemSoftDeleted(itemId, ownerId);
      } catch (RuntimeException e) {
        log.warn("Item {} 生命周期回调失败：{}", itemId, e.getMessage());
      }
    }
  }

  private void submitDeleteTask(Long itemId, long ownerId) {
    try {
      taskSubmissionService.submit(
          ProcessingConstants.TASK_TYPE_KNOWLEDGE_DELETE,
          businessKey(itemId, 0) + KnowledgeConstants.BUSINESS_KEY_DELETE_SUFFIX,
          itemId,
          ownerId,
          null,
          DEFAULT_MAX_RETRIES);
    } catch (DuplicateKeyException e) {
      // 已有活动删除任务，跳过
    }
  }

  private KnowledgeItem getByIdAndOwnerInternal(Long id) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    KnowledgeItem item =
        itemMapper.selectOne(
            new LambdaQueryWrapper<KnowledgeItem>()
                .eq(KnowledgeItem::getId, id)
                .eq(KnowledgeItem::getOwnerId, ownerId)
                .eq(KnowledgeItem::getStatus, KnowledgeConstants.STATUS_ACTIVE));
    if (item == null) {
      throw new KnowledgeItemNotFoundException(id);
    }
    return item;
  }

  private static void requireExpectedVersion(KnowledgeItem item, int rowVersion) {
    if (item.getRowVersion() == null || item.getRowVersion() != rowVersion) {
      throw new KnowledgeItemVersionConflictException();
    }
  }

  private List<Long> resolveKnowledgeBaseIds(long ownerId, List<String> rawIds) {
    if (rawIds == null || rawIds.isEmpty()) {
      throw new IllegalArgumentException("At least one KnowledgeBase must be associated");
    }
    List<Long> ids = new ArrayList<>();
    for (String raw : rawIds) {
      Long id = Long.valueOf(raw);
      knowledgeBaseService.getByIdAndOwner(id); // 校验存在与 owner 边界
      ids.add(id);
    }
    return new ArrayList<>(new LinkedHashSet<>(ids));
  }

  private List<Long> resolveLongKnowledgeBaseIds(long ownerId, List<Long> rawIds) {
    if (rawIds == null || rawIds.isEmpty()) {
      throw new IllegalArgumentException("At least one KnowledgeBase must be associated");
    }
    List<Long> ids = new ArrayList<>();
    for (Long id : rawIds) {
      knowledgeBaseService.getByIdAndOwner(id); // 校验存在与 owner 边界
      ids.add(id);
    }
    return new ArrayList<>(new LinkedHashSet<>(ids));
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

  private static String businessKey(Long itemId, int contentVersion) {
    return KnowledgeConstants.BUSINESS_KEY_PREFIX
        + itemId
        + KnowledgeConstants.BUSINESS_KEY_DELIMITER
        + contentVersion;
  }

  private boolean kbRelationsChanged(long ownerId, Long itemId, List<Long> newIds) {
    List<Long> currentIds =
        kbItemMapper
            .selectList(
                new LambdaQueryWrapper<KnowledgeBaseItem>()
                    .eq(KnowledgeBaseItem::getOwnerId, ownerId)
                    .eq(KnowledgeBaseItem::getKnowledgeItemId, itemId)
                    .eq(KnowledgeBaseItem::getDeleted, false))
            .stream()
            .map(KnowledgeBaseItem::getKnowledgeBaseId)
            .sorted()
            .toList();
    return !currentIds.equals(newIds.stream().sorted().toList());
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
    return itemTagMapper
        .selectList(
            new LambdaQueryWrapper<KnowledgeItemTag>()
                .eq(KnowledgeItemTag::getOwnerId, ownerId)
                .eq(KnowledgeItemTag::getKnowledgeItemId, itemId)
                .eq(KnowledgeItemTag::getDeleted, false))
        .stream()
        .map(KnowledgeItemTag::getTagId)
        .toList();
  }

  private void replaceKnowledgeBaseRelations(long ownerId, Long itemId, List<Long> newKbIds) {
    List<KnowledgeBaseItem> existing =
        kbItemMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBaseItem>()
                .eq(KnowledgeBaseItem::getOwnerId, ownerId)
                .eq(KnowledgeBaseItem::getKnowledgeItemId, itemId));
    Set<Long> newSet = new HashSet<>(newKbIds);
    for (KnowledgeBaseItem rel : existing) {
      boolean keep = newSet.remove(rel.getKnowledgeBaseId());
      if (keep) {
        if (rel.getDeleted()) {
          rel.setDeleted(false);
          kbItemMapper.updateById(rel);
        }
      } else if (!rel.getDeleted()) {
        rel.setDeleted(true);
        kbItemMapper.updateById(rel);
      }
    }
    for (Long kbId : newSet) {
      KnowledgeBaseItem fresh = new KnowledgeBaseItem();
      fresh.setOwnerId(ownerId);
      fresh.setKnowledgeBaseId(kbId);
      fresh.setKnowledgeItemId(itemId);
      fresh.setDeleted(false);
      kbItemMapper.insert(fresh);
    }
  }

  private void replaceTagRelations(long ownerId, Long itemId, List<String> newNames) {
    List<KnowledgeItemTag> existing =
        itemTagMapper.selectList(
            new LambdaQueryWrapper<KnowledgeItemTag>()
                .eq(KnowledgeItemTag::getOwnerId, ownerId)
                .eq(KnowledgeItemTag::getKnowledgeItemId, itemId));
    Set<Long> keepTagIds = new HashSet<>();
    for (String name : newNames) {
      Long tagId = findOrCreateTag(ownerId, name);
      keepTagIds.add(tagId);
    }

    for (KnowledgeItemTag rel : existing) {
      if (!keepTagIds.contains(rel.getTagId()) && !rel.getDeleted()) {
        rel.setDeleted(true);
        itemTagMapper.updateById(rel);
      }
    }

    // 需要建立关联的 tag：没有活跃关联则插入或恢复
    List<Long> activeTagIds = listActiveTagIds(ownerId, itemId);
    for (Long tagId : keepTagIds) {
      if (activeTagIds.contains(tagId)) {
        continue;
      }
      KnowledgeItemTag stale =
          existing.stream().filter(r -> r.getTagId().equals(tagId)).findFirst().orElse(null);
      if (stale != null) {
        stale.setDeleted(false);
        itemTagMapper.updateById(stale);
      } else {
        KnowledgeItemTag fresh = new KnowledgeItemTag();
        fresh.setOwnerId(ownerId);
        fresh.setKnowledgeItemId(itemId);
        fresh.setTagId(tagId);
        fresh.setDeleted(false);
        itemTagMapper.insert(fresh);
      }
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
