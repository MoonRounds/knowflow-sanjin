package knowflow.sanjin.modules.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import knowflow.sanjin.modules.document.config.DocumentProperties;
import knowflow.sanjin.modules.document.exception.FileTooLargeException;
import knowflow.sanjin.modules.document.exception.InvalidFileContentException;
import knowflow.sanjin.modules.document.vo.FileUploadResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 上传事务协调器：并发相同文件都通过预查时，唯一约束只允许一个赢家；输家等待赢家提交后回查并返回同一 Item。
 *
 * <p>请求流先复制到本地临时文件，保证第一次事务回滚后仍能在独立事务中解析去重身份并查询赢家。
 */
@Service
public class DocumentUploadCoordinator {

  private final DocumentUploadService uploadService;
  private final MimeDetectionService mimeDetection;
  private final DocumentProperties properties;

  public DocumentUploadCoordinator(
      DocumentUploadService uploadService,
      MimeDetectionService mimeDetection,
      DocumentProperties properties) {
    this.uploadService = uploadService;
    this.mimeDetection = mimeDetection;
    this.properties = properties;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public FileUploadResponse upload(
      String originalFilename,
      String declaredContentType,
      InputStream input,
      String knowledgeBaseIdsJson) {
    Path retryableCopy = null;
    try {
      Path requestTempDir = properties.storageRootPath().resolve("tmp");
      Files.createDirectories(requestTempDir);
      retryableCopy = Files.createTempFile(requestTempDir, "upload-request-", ".tmp");
      copyWithLimit(input, retryableCopy, properties.getMaxFileBytes());
      try (InputStream first = Files.newInputStream(retryableCopy)) {
        return uploadService.upload(
            originalFilename, declaredContentType, first, knowledgeBaseIdsJson);
      } catch (DataIntegrityViolationException conflict) {
        String detectedMime;
        try (InputStream mimeInput = Files.newInputStream(retryableCopy)) {
          detectedMime = mimeDetection.detectAndValidate(mimeInput);
        } catch (MimeDetectionService.UnsupportedContentException e) {
          throw new InvalidFileContentException(e.getMessage());
        }
        String sha256 = sha256(retryableCopy);
        // InnoDB 唯一键检查会等待竞争事务结束；异常返回时赢家已提交，可直接在新事务回查。
        try {
          return uploadService.findDuplicateAfterConflict(
              detectedMime, sha256, knowledgeBaseIdsJson);
        } catch (IllegalStateException noWinner) {
          // 只把确定命中去重约束的竞争转成重复响应；其他完整性错误保留原始异常语义。
          conflict.addSuppressed(noWinner);
          throw conflict;
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("上传请求暂存失败", e);
    } finally {
      if (retryableCopy != null) {
        try {
          Files.deleteIfExists(retryableCopy);
        } catch (IOException ignored) {
          // FileStorageService 的孤儿清理可兜底；不得覆盖原始上传结果。
        }
      }
    }
  }

  private static void copyWithLimit(InputStream input, Path target, long maxBytes)
      throws IOException {
    long total = 0;
    try (var output = Files.newOutputStream(target)) {
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = input.read(buffer)) != -1) {
        total += read;
        if (total > maxBytes) {
          throw new FileTooLargeException("文件超过大小上限 " + maxBytes + " 字节");
        }
        output.write(buffer, 0, read);
      }
    }
  }

  private static String sha256(Path file) throws IOException {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(file)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
          digest.update(buffer, 0, read);
        }
      }
      return java.util.HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
