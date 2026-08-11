package knowflow.sanjin.modules.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import knowflow.sanjin.modules.document.DocumentConstants;
import knowflow.sanjin.modules.document.entity.FileMetadata;
import knowflow.sanjin.modules.document.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.knowledge.service.KnowledgeItemLifecycleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Upload 生命周期回调：Item 软删时同步软删 FileMetadata 并删除原文件（先删文件后置状态，与删除语义 DECISIONS §14 对齐）。
 *
 * <p>自身异常不抛出，避免影响 knowledge 模块删除主流程；孤儿由存储清理兜底。
 */
@Component
public class UploadFileLifecycleHandler implements KnowledgeItemLifecycleHandler {

  private static final Logger log = LoggerFactory.getLogger(UploadFileLifecycleHandler.class);

  private final FileMetadataMapper fileMapper;
  private final LocalFileStore localFileStore;

  public UploadFileLifecycleHandler(FileMetadataMapper fileMapper, LocalFileStore localFileStore) {
    this.fileMapper = fileMapper;
    this.localFileStore = localFileStore;
  }

  @Override
  public void onItemSoftDeleted(Long itemId, long ownerId) {
    try {
      FileMetadata file =
          fileMapper.selectOne(
              new LambdaQueryWrapper<FileMetadata>()
                  .eq(FileMetadata::getKnowledgeItemId, itemId)
                  .eq(FileMetadata::getOwnerId, ownerId)
                  .last("LIMIT 1"));
      if (file == null) {
        return;
      }
      // 先删磁盘文件，再置状态为 DELETED（删除不级联 Item；FileMetadata 与 Item 一起软删）
      localFileStore.deleteStored(file.getStorageKey());
      file.setStatus(DocumentConstants.FILE_STATUS_DELETED);
      fileMapper.updateById(file);
      log.info("Item 软删：已清理原文件 fileId={} itemId={}", file.getId(), itemId);
    } catch (RuntimeException e) {
      log.warn("Item {} 软删时清理原文件失败：{}", itemId, e.getMessage());
    }
  }
}
