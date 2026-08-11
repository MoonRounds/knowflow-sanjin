package knowflow.sanjin.modules.document.service;

import java.io.InputStream;
import java.util.Locale;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.document.DocumentConstants;
import knowflow.sanjin.modules.document.entity.FileMetadata;
import knowflow.sanjin.modules.document.exception.RetryableDocumentException;
import knowflow.sanjin.modules.document.exception.TerminalDocumentException;
import knowflow.sanjin.modules.document.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.service.KnowledgeService;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文档解析执行器：读取原文件 → 解析为规范正文 → 更新 Item content/title → 标记文件解析成功 → 提交 KNOWLEDGE_INDEX 任务。
 *
 * <p>幂等：解析任务 business key 唯一；重复消费时重新解析并覆盖同一 Item 内容，不会创建第二个 Item。解析失败抛 {@link
 * RetryableDocumentException}（可重试）或 {@link TerminalDocumentException}（终态）。
 */
@Service
public class DocumentParsingService {

  private static final Logger log = LoggerFactory.getLogger(DocumentParsingService.class);

  private final FileMetadataMapper fileMapper;
  private final KnowledgeItemMapper itemMapper;
  private final KnowledgeService knowledgeService;
  private final DocumentParser parser;
  private final LocalFileStore localFileStore;
  private final FileStorageService storage;

  public DocumentParsingService(
      FileMetadataMapper fileMapper,
      KnowledgeItemMapper itemMapper,
      KnowledgeService knowledgeService,
      DocumentParser parser,
      LocalFileStore localFileStore,
      FileStorageService storage) {
    this.fileMapper = fileMapper;
    this.itemMapper = itemMapper;
    this.knowledgeService = knowledgeService;
    this.parser = parser;
    this.localFileStore = localFileStore;
    this.storage = storage;
  }

  /** 执行解析。成功返回；失败抛可重试或终态文档异常。 */
  public void execute(ProcessingTask task)
      throws RetryableDocumentException, TerminalDocumentException {
    long fileId = task.getBusinessId();
    FileMetadata file = fileMapper.selectById(fileId);
    if (file == null) {
      throw new TerminalDocumentException("FileMetadata " + fileId + " 不存在");
    }
    KnowledgeItem item = itemMapper.selectById(file.getKnowledgeItemId());
    if (item == null) {
      throw new TerminalDocumentException("KnowledgeItem " + file.getKnowledgeItemId() + " 不存在");
    }
    if (localFileStore.missing(file.getStorageKey())) {
      throw new TerminalDocumentException(
          ErrorCode.FILE_STORED_MISSING, "原文件缺失（存储键=" + file.getStorageKey() + "）");
    }

    boolean isMarkdown = isMarkdownFile(file.getOriginalFilename());
    DocumentParser.ParsedDocument parsed;
    try (InputStream in = storage.read(file.getStorageKey())) {
      parsed = parser.parse(in, file.getOriginalFilename(), isMarkdown);
    } catch (java.io.IOException e) {
      throw new RetryableDocumentException(
          ErrorCode.DOCUMENT_PARSE_READ_FAILED, "读取原文件失败：" + e.getMessage(), e);
    } catch (knowflow.sanjin.modules.document.exception.DocumentParseException e) {
      throw new RetryableDocumentException(
          ErrorCode.DOCUMENT_PARSE_FAILED, "文档解析失败：" + e.getMessage(), e);
    }

    // 更新 Item 内容与 title（contentVersion 保持 1；索引由独立任务推进 indexedVersion）
    KnowledgeItem update = new KnowledgeItem();
    update.setId(item.getId());
    update.setContent(parsed.content());
    update.setTitle(parsed.title());
    itemMapper.updateById(update);

    // 标记文件解析成功
    FileMetadata statusUpdate = new FileMetadata();
    statusUpdate.setId(file.getId());
    statusUpdate.setParseStatus(DocumentConstants.PARSE_STATUS_SUCCEEDED);
    statusUpdate.setParseErrorCode(null);
    statusUpdate.setParseErrorMessage(null);
    fileMapper.updateById(statusUpdate);

    // 解析成功后提交索引任务（已有活动任务则跳过，幂等）
    knowledgeService.submitIndexTaskAfterParse(item.getId(), item.getOwnerId(), 1);
    log.info(
        "文档解析成功 fileId={} itemId={} contentChars={}",
        fileId,
        item.getId(),
        parsed.content().length());
  }

  private boolean isMarkdownFile(String filename) {
    if (filename == null) {
      return false;
    }
    String lower = filename.toLowerCase(Locale.ROOT);
    return lower.endsWith(".md") || lower.endsWith(".markdown");
  }
}
