package knowflow.sanjin.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import knowflow.sanjin.modules.file.FileConstants;
import knowflow.sanjin.modules.file.config.FileProperties;
import knowflow.sanjin.modules.file.controller.FileMetadataAssembler;
import knowflow.sanjin.modules.file.entity.FileMetadata;
import knowflow.sanjin.modules.file.exception.FileMetadataNotFoundException;
import knowflow.sanjin.modules.file.exception.FileUnsupportedTypeException;
import knowflow.sanjin.modules.file.exception.InvalidFileContentException;
import knowflow.sanjin.modules.file.exception.StoredFileMissingException;
import knowflow.sanjin.modules.file.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.file.vo.FileMetadataResponse;
import knowflow.sanjin.modules.file.vo.FileUploadResponse;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.service.KnowledgeDocumentService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 上传应用服务：流式接收 → 大小/扩展名校验 → Tika MIME 检测 → SHA-256 去重 → 创建 FileMetadata + KnowledgeItem + 提交
 * DOCUMENT_PARSE 任务。
 *
 * <p>去重身份为 owner + 检测 MIME + 原始内容 SHA-256（文件名不参与）。事务边界：文件正式落盘与数据库写入在同一事务；数据库提交失败或中途异常时补偿删除
 * 已落盘文件。活动重复返回已有 Document（不重复创建/索引）；软删除重复触发恢复语义；活动但磁盘文件缺失时用本次上传字节修复。
 */
@Service
public class FileUploadService {

  private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

  private static final int DEFAULT_MAX_RETRIES = 3;

  private final FileMetadataMapper fileMapper;
  private final CurrentOwnerProvider currentOwnerProvider;
  private final FileProperties properties;
  private final MimeDetectionService mimeDetection;
  private final FileStorageService storage;
  private final LocalFileStore localFileStore;
  private final KnowledgeDocumentService knowledgeService;
  private final TaskSubmissionService taskSubmissionService;

  public FileUploadService(
      FileMetadataMapper fileMapper,
      CurrentOwnerProvider currentOwnerProvider,
      FileProperties properties,
      MimeDetectionService mimeDetection,
      FileStorageService storage,
      LocalFileStore localFileStore,
      KnowledgeDocumentService knowledgeService,
      TaskSubmissionService taskSubmissionService) {
    this.fileMapper = fileMapper;
    this.currentOwnerProvider = currentOwnerProvider;
    this.properties = properties;
    this.mimeDetection = mimeDetection;
    this.storage = storage;
    this.localFileStore = localFileStore;
    this.knowledgeService = knowledgeService;
    this.taskSubmissionService = taskSubmissionService;
  }

  /**
   * 上传单个文件到指定 KnowledgeBase。
   *
   * @param knowledgeBaseId 目标知识库 id（必填）
   */
  @Transactional
  public FileUploadResponse upload(
      String originalFilename, String declaredContentType, InputStream in, String knowledgeBaseId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    validateExtension(originalFilename);
    String normalizedFilename = sanitizeFilename(originalFilename);
    Long kbId = parseKnowledgeBaseId(knowledgeBaseId);

    // 1. 流式接收至临时文件，同时计算原始字节 SHA-256；超过大小上限即中断并清理临时文件
    FileStorageService.StagedFile staged = null;
    try {
      staged = storage.stage(in, properties.getMaxFileBytes());
    } catch (IOException e) {
      throw new IllegalStateException("文件写入失败", e);
    }

    try {
      // 2. MIME 与文本有效性检测（读取实际字节，不信任扩展名/Content-Type）
      String detectedMime;
      try (InputStream fileIn = Files.newInputStream(staged.path())) {
        detectedMime = mimeDetection.detectAndValidate(fileIn);
      } catch (MimeDetectionService.UnsupportedContentException e) {
        throw new InvalidFileContentException(e.getMessage());
      } catch (IOException e) {
        throw new IllegalStateException("文件读取失败", e);
      }

      // 3. 去重：owner + 检测 MIME + 原始 SHA-256
      FileMetadata existing = findDedup(ownerId, detectedMime, staged.sha256());
      if (existing != null) {
        try {
          return handleDuplicate(existing, staged, kbId);
        } catch (IOException e) {
          throw new IllegalStateException("重复文件落盘失败", e);
        }
      }

      // 4. 新文件
      try {
        return createNew(
            ownerId, normalizedFilename, declaredContentType, detectedMime, staged, kbId);
      } catch (IOException e) {
        throw new IllegalStateException("文件落盘失败", e);
      }
    } finally {
      if (staged != null) {
        localFileStore.deleteTempQuietly(staged.path());
      }
    }
  }

  /** 唯一约束竞争失败后的回查入口；使用新事务，避免在已标记 rollback-only 的上传事务中继续查询。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public FileUploadResponse findDuplicateAfterConflict(
      String detectedMime, String sha256, String knowledgeBaseId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    FileMetadata winner = findDedup(ownerId, detectedMime, sha256);
    if (winner == null) {
      throw new IllegalStateException("并发上传冲突后未找到已提交文件");
    }
    KnowledgeDocument item =
        knowledgeService.getByIdAndOwnerIncludingDeleted(winner.getKnowledgeDocumentId());
    if (!FileConstants.FILE_STATUS_ACTIVE.equals(winner.getStatus())
        || Boolean.TRUE.equals(item.getDeleted())
        || localFileStore.missing(winner.getStorageKey())) {
      throw new IllegalStateException("并发上传赢家尚未形成可复用的活动文件");
    }
    // 唯一键冲突的赢家是刚提交的新上传；这里只读返回，不能在 read-only 回查事务中执行恢复/修复。
    parseKnowledgeBaseId(knowledgeBaseId);
    return FileMetadataAssembler.toUploadResponse(winner, item, true);
  }

  /** 创建新文件：正式落盘 → KnowledgeDocument → FileMetadata → 提交解析任务。 */
  private FileUploadResponse createNew(
      long ownerId,
      String originalFilename,
      String declaredContentType,
      String detectedMime,
      FileStorageService.StagedFile staged,
      Long kbId)
      throws IOException {
    String storageKey = storage.newStorageKey();
    storage.commit(staged, storageKey);
    try {
      KnowledgeDocument item =
          knowledgeService.createUploadItem(kbId, titleFromFilename(originalFilename));
      FileMetadata file = new FileMetadata();
      file.setOwnerId(ownerId);
      file.setKnowledgeDocumentId(item.getId());
      file.setStorageKey(storageKey);
      file.setOriginalFilename(originalFilename);
      file.setContentType(
          declaredContentType != null ? declaredContentType : "application/octet-stream");
      file.setDetectedMimeType(detectedMime);
      file.setByteSize(sizeOf(staged));
      file.setSha256(staged.sha256());
      file.setStatus(FileConstants.FILE_STATUS_ACTIVE);
      file.setParseStatus(FileConstants.PARSE_STATUS_PENDING);
      fileMapper.insert(file);

      submitParseTask(file);
      log.info("文件已上传 fileId={} documentId={} sha={}", file.getId(), item.getId(), staged.sha256());
      return FileMetadataAssembler.toUploadResponse(file, item, false);
    } catch (RuntimeException e) {
      storage.delete(storageKey);
      throw e;
    }
  }

  /**
   * 去重命中处理：
   *
   * <ul>
   *   <li>活动且磁盘文件正常：复用已有 FileMetadata/Document（不重复创建、不重复索引）；
   *   <li>活动但磁盘文件缺失：用本次上传字节修复存储键；
   *   <li>软删除（文件或 Document）：恢复为活动并重新解析。
   * </ul>
   */
  private FileUploadResponse handleDuplicate(
      FileMetadata existing, FileStorageService.StagedFile staged, Long requestedKbId)
      throws IOException {
    // 去重命中可能指向 DELETED Document（软删恢复场景），需含 DELETED 的 owner 查询
    KnowledgeDocument item =
        knowledgeService.getByIdAndOwnerIncludingDeleted(existing.getKnowledgeDocumentId());
    boolean fileDeleted = FileConstants.FILE_STATUS_DELETED.equals(existing.getStatus());
    boolean itemDeleted = Boolean.TRUE.equals(item.getDeleted());

    if (fileDeleted || itemDeleted) {
      return restore(existing, staged, requestedKbId);
    }
    if (localFileStore.missing(existing.getStorageKey())) {
      repairStoredFile(existing, staged);
      return FileMetadataAssembler.toUploadResponse(existing, item, true);
    }
    return FileMetadataAssembler.toUploadResponse(existing, item, true);
  }

  /** 软删除恢复：重新落盘新字节、复用 FileMetadata 行、恢复 Document 活动并重新解析。 */
  private FileUploadResponse restore(
      FileMetadata existing, FileStorageService.StagedFile staged, Long requestedKbId)
      throws IOException {
    String newKey = storage.newStorageKey();
    storage.commit(staged, newKey);
    try {
      existing.setStorageKey(newKey);
      existing.setStatus(FileConstants.FILE_STATUS_ACTIVE);
      existing.setParseStatus(FileConstants.PARSE_STATUS_PENDING);
      existing.setParseErrorCode(null);
      existing.setParseErrorMessage(null);
      fileMapper.updateById(existing);
      // 恢复 Document（重新归属 KB 与重置正文占位，索引由解析成功后触发）
      knowledgeService.restoreUploadItem(existing.getKnowledgeDocumentId(), requestedKbId);
      submitParseTask(existing);
      KnowledgeDocument restored =
          knowledgeService.getByIdAndOwnerIncludingDeleted(existing.getKnowledgeDocumentId());
      log.info(
          "重复文件恢复 fileId={} documentId={}", existing.getId(), existing.getKnowledgeDocumentId());
      return FileMetadataAssembler.toUploadResponse(existing, restored, false);
    } catch (RuntimeException e) {
      storage.delete(newKey);
      throw e;
    }
  }

  /** 活动文件但磁盘缺失：用本次上传字节修复存储键（内容相同，无需重新解析/索引）。 */
  private void repairStoredFile(FileMetadata existing, FileStorageService.StagedFile staged)
      throws IOException {
    String newKey = storage.newStorageKey();
    storage.commit(staged, newKey);
    try {
      existing.setStorageKey(newKey);
      fileMapper.updateById(existing);
      log.info("修复缺失原文件 fileId={} newKey={}", existing.getId(), newKey);
    } catch (RuntimeException e) {
      storage.delete(newKey);
      throw e;
    }
  }

  private void submitParseTask(FileMetadata file) {
    try {
      taskSubmissionService.submit(
          FileConstants.TASK_TYPE_DOCUMENT_PARSE,
          FileConstants.BUSINESS_KEY_PREFIX + file.getId(),
          file.getId(),
          file.getOwnerId(),
          null,
          DEFAULT_MAX_RETRIES,
          FileConstants.WORK_QUEUE_BASE);
    } catch (DuplicateKeyException e) {
      // 已有活动解析任务，跳过
    }
  }

  private void validateExtension(String filename) {
    if (filename == null) {
      throw new FileUnsupportedTypeException("文件名为空");
    }
    String lower = filename.toLowerCase(Locale.ROOT);
    for (String ext : properties.getAllowedExtensions()) {
      if (lower.endsWith("." + ext)) {
        return;
      }
    }
    throw new FileUnsupportedTypeException("仅支持 .md / .markdown / .txt 文件");
  }

  private FileMetadata findDedup(long ownerId, String detectedMime, String sha256) {
    return fileMapper.selectOne(
        new LambdaQueryWrapper<FileMetadata>()
            .eq(FileMetadata::getOwnerId, ownerId)
            .eq(FileMetadata::getDetectedMimeType, detectedMime)
            .eq(FileMetadata::getSha256, sha256)
            .last("LIMIT 1"));
  }

  private Long parseKnowledgeBaseId(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("至少需要一个知识库");
    }
    try {
      return Long.valueOf(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("知识库 id 格式非法：" + e.getMessage());
    }
  }

  private static String sanitizeFilename(String filename) {
    if (filename == null) {
      return "untitled.txt";
    }
    String base = filename.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    return slash >= 0 ? base.substring(slash + 1) : base;
  }

  private static String titleFromFilename(String filename) {
    String base = sanitizeFilename(filename);
    if (base.isBlank()) {
      return "Untitled File";
    }
    int dot = base.lastIndexOf('.');
    String name = dot > 0 ? base.substring(0, dot) : base;
    return name.isBlank() ? "Untitled File" : name;
  }

  private static long sizeOf(FileStorageService.StagedFile staged) {
    try {
      return Files.size(staged.path());
    } catch (IOException e) {
      return 0L;
    }
  }

  /** 下载原文件：owner 校验 + 校验存储文件存在；返回输入流与展示文件名。 */
  @Transactional(readOnly = true)
  public DownloadedFile download(Long fileId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    FileMetadata file = getByIdAndOwner(fileId, ownerId);
    if (localFileStore.missing(file.getStorageKey())) {
      throw new StoredFileMissingException("原文件缺失，存储键=" + file.getStorageKey());
    }
    try {
      InputStream in = storage.read(file.getStorageKey());
      return new DownloadedFile(in, file.getOriginalFilename(), file.getDetectedMimeType());
    } catch (IOException e) {
      throw new StoredFileMissingException("原文件读取失败：" + e.getMessage());
    }
  }

  @Transactional(readOnly = true)
  public FileMetadataResponse getById(Long fileId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    return FileMetadataAssembler.toResponse(getByIdAndOwner(fileId, ownerId));
  }

  /** 按 Document id 查询文件元数据；无则返回 null（Manual Note / Candidate Document 无文件）。 */
  @Transactional(readOnly = true)
  public FileMetadataResponse getByDocumentId(Long documentId) {
    long ownerId = currentOwnerProvider.getCurrentOwnerId();
    FileMetadata file =
        fileMapper.selectOne(
            new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getKnowledgeDocumentId, documentId)
                .eq(FileMetadata::getOwnerId, ownerId)
                .last("LIMIT 1"));
    return file == null ? null : FileMetadataAssembler.toResponse(file);
  }

  @Transactional(readOnly = true)
  public FileMetadata getByIdAndOwner(Long fileId, long ownerId) {
    FileMetadata file =
        fileMapper.selectOne(
            new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getId, fileId)
                .eq(FileMetadata::getOwnerId, ownerId));
    if (file == null) {
      throw new FileMetadataNotFoundException(fileId);
    }
    return file;
  }

  /** 下载结果：输入流 + 展示文件名 + MIME。 */
  public record DownloadedFile(InputStream in, String filename, String contentType) {}
}
