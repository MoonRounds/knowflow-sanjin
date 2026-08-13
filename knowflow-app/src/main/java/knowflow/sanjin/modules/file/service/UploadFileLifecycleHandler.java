package knowflow.sanjin.modules.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import knowflow.sanjin.modules.file.FileConstants;
import knowflow.sanjin.modules.file.entity.FileMetadata;
import knowflow.sanjin.modules.file.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.knowledge.service.KnowledgeDocumentLifecycleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Upload 生命周期回调：Item 软删时同步软删 FileMetadata 并删除原文件（先删文件后置状态，与删除语义 DECISIONS §14 对齐）。
 *
 * <p>自身异常不抛出，避免影响 knowledge 模块删除主流程；孤儿由存储清理兜底。
 */
@Component
public class UploadFileLifecycleHandler implements KnowledgeDocumentLifecycleHandler {

  private static final Logger log = LoggerFactory.getLogger(UploadFileLifecycleHandler.class);

  private final FileMetadataMapper fileMapper;
  private final LocalFileStore localFileStore;

  public UploadFileLifecycleHandler(FileMetadataMapper fileMapper, LocalFileStore localFileStore) {
    this.fileMapper = fileMapper;
    this.localFileStore = localFileStore;
  }

  @Override
  public void onDocumentSoftDeleted(Long documentId, long ownerId) {
    try {
      FileMetadata file =
          fileMapper.selectOne(
              new LambdaQueryWrapper<FileMetadata>()
                  .eq(FileMetadata::getKnowledgeDocumentId, documentId)
                  .eq(FileMetadata::getOwnerId, ownerId)
                  .last("LIMIT 1"));
      if (file == null) {
        return;
      }
      // 先删磁盘文件，再置状态为 DELETED（删除不级联 Document；FileMetadata 与 Document 一起软删）
      localFileStore.deleteStored(file.getStorageKey());
      file.setStatus(FileConstants.FILE_STATUS_DELETED);
      fileMapper.updateById(file);
      log.info("Document 软删：已清理原文件 fileId={} documentId={}", file.getId(), documentId);
    } catch (RuntimeException e) {
      log.warn("Document {} 软删时清理原文件失败：{}", documentId, e.getMessage());
    }
  }
}
