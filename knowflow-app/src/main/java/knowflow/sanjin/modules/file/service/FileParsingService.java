package knowflow.sanjin.modules.file.service;

import java.io.InputStream;
import java.util.Locale;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.common.util.ObsLog;
import knowflow.sanjin.modules.file.FileConstants;
import knowflow.sanjin.modules.file.entity.FileMetadata;
import knowflow.sanjin.modules.file.exception.RetryableFileException;
import knowflow.sanjin.modules.file.exception.TerminalFileException;
import knowflow.sanjin.modules.file.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.service.KnowledgeDocumentService;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文档解析执行器：读取原文件 → 解析为规范正文 → 更新 Item content/title → 标记文件解析成功 → 提交 KNOWLEDGE_INDEX 任务。
 *
 * <p>幂等：解析任务 business key 唯一；重复消费时重新解析并覆盖同一 Item 内容，不会创建第二个 Item。解析失败抛 {@link
 * RetryableFileException}（可重试）或 {@link TerminalFileException}（终态）。
 */
@Service
public class FileParsingService {

  private static final Logger log = LoggerFactory.getLogger(FileParsingService.class);

  private final FileMetadataMapper fileMapper;
  private final KnowledgeDocumentMapper documentMapper;
  private final KnowledgeDocumentService knowledgeService;
  private final FileParser parser;
  private final LocalFileStore localFileStore;
  private final FileStorageService storage;

  public FileParsingService(
      FileMetadataMapper fileMapper,
      KnowledgeDocumentMapper documentMapper,
      KnowledgeDocumentService knowledgeService,
      FileParser parser,
      LocalFileStore localFileStore,
      FileStorageService storage) {
    this.fileMapper = fileMapper;
    this.documentMapper = documentMapper;
    this.knowledgeService = knowledgeService;
    this.parser = parser;
    this.localFileStore = localFileStore;
    this.storage = storage;
  }

  /** 执行解析。成功返回；失败抛可重试或终态文档异常。 */
  public void execute(ProcessingTask task) throws RetryableFileException, TerminalFileException {
    long fileId = task.getBusinessId();
    FileMetadata file = fileMapper.selectById(fileId);
    if (file == null) {
      throw new TerminalFileException("FileMetadata " + fileId + " 不存在");
    }
    KnowledgeDocument item = documentMapper.selectById(file.getKnowledgeDocumentId());
    if (item == null) {
      throw new TerminalFileException(
          "KnowledgeDocument " + file.getKnowledgeDocumentId() + " 不存在");
    }
    if (localFileStore.missing(file.getStorageKey())) {
      throw new TerminalFileException(
          ErrorCode.FILE_STORED_MISSING, "原文件缺失（存储键=" + file.getStorageKey() + "）");
    }

    boolean isMarkdown = isMarkdownFile(file.getOriginalFilename());
    long parseStart = System.nanoTime();
    FileParser.ParsedFile parsed;
    try (InputStream in = storage.read(file.getStorageKey())) {
      parsed = parser.parse(in, file.getOriginalFilename(), isMarkdown);
    } catch (java.io.IOException e) {
      throw new RetryableFileException(
          ErrorCode.DOCUMENT_PARSE_READ_FAILED, "读取原文件失败：" + e.getMessage(), e);
    } catch (knowflow.sanjin.modules.file.exception.FileParseException e) {
      throw new RetryableFileException(
          ErrorCode.DOCUMENT_PARSE_FAILED, "文档解析失败：" + e.getMessage(), e);
    }

    // 更新 Document 内容与 title；初次上传为 v1，软删恢复会预先递增版本。
    KnowledgeDocument update = new KnowledgeDocument();
    update.setId(item.getId());
    update.setContent(parsed.content());
    update.setTitle(parsed.title());
    documentMapper.updateById(update);

    // 标记文件解析成功
    FileMetadata statusUpdate = new FileMetadata();
    statusUpdate.setId(file.getId());
    statusUpdate.setParseStatus(FileConstants.PARSE_STATUS_SUCCEEDED);
    statusUpdate.setParseErrorCode(null);
    statusUpdate.setParseErrorMessage(null);
    fileMapper.updateById(statusUpdate);

    // 解析成功后提交索引任务（已有活动任务则跳过，幂等）
    knowledgeService.submitIndexTaskAfterParse(
        item.getId(), item.getOwnerId(), item.getContentVersion());
    log.info(
        "文档解析成功 fileId={} documentId={} 标题={} 内容字符数={} 文件类型={} 解析耗时={}",
        fileId,
        item.getId(),
        parsed.title(),
        parsed.content().length(),
        isMarkdown ? "Markdown" : "TXT",
        ObsLog.elapsedMs(parseStart));
  }

  private boolean isMarkdownFile(String filename) {
    if (filename == null) {
      return false;
    }
    String lower = filename.toLowerCase(Locale.ROOT);
    return lower.endsWith(".md") || lower.endsWith(".markdown");
  }
}
