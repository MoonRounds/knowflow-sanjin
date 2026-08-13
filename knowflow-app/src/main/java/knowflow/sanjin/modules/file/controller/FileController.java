package knowflow.sanjin.modules.file.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import knowflow.sanjin.common.util.ApiValueParser;
import knowflow.sanjin.modules.file.exception.InvalidFileContentException;
import knowflow.sanjin.modules.file.service.FileUploadCoordinator;
import knowflow.sanjin.modules.file.service.FileUploadService;
import knowflow.sanjin.modules.file.vo.FileMetadataResponse;
import knowflow.sanjin.modules.file.vo.FileUploadResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文档上传与下载 REST 入口：multipart 上传、owner 校验下载、文件元数据查看。 */
@RestController
@RequestMapping("${knowflow.api.base-path:/api/v1}")
public class FileController {

  private final FileUploadService uploadService;
  private final FileUploadCoordinator uploadCoordinator;

  public FileController(FileUploadService uploadService, FileUploadCoordinator uploadCoordinator) {
    this.uploadService = uploadService;
    this.uploadCoordinator = uploadCoordinator;
  }

  @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<FileUploadResponse> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId) {
    if (file == null || file.isEmpty()) {
      throw new InvalidFileContentException("文件为空");
    }
    String originalFilename = file.getOriginalFilename();
    String declaredContentType = file.getContentType();
    try (InputStream in = file.getInputStream()) {
      FileUploadResponse response =
          uploadCoordinator.upload(originalFilename, declaredContentType, in, knowledgeBaseId);
      return ResponseEntity.ok(response);
    } catch (IOException e) {
      throw new IllegalStateException("读取上传文件失败", e);
    }
  }

  @GetMapping("/files/{id}")
  public ResponseEntity<FileMetadataResponse> get(@PathVariable String id) {
    return ResponseEntity.ok(uploadService.getById(ApiValueParser.positiveId(id, "id")));
  }

  /** 按 Document 查询文件元数据；Manual Note / Candidate Document 返回 null。 */
  @GetMapping("/documents/{documentId}/file")
  public ResponseEntity<FileMetadataResponse> getByDocument(@PathVariable String documentId) {
    return ResponseEntity.ok(
        uploadService.getByDocumentId(ApiValueParser.positiveId(documentId, "documentId")));
  }

  @GetMapping("/files/{id}/download")
  public ResponseEntity<org.springframework.core.io.InputStreamResource> download(
      @PathVariable String id) {
    FileUploadService.DownloadedFile downloaded =
        uploadService.download(ApiValueParser.positiveId(id, "id"));
    // Content-Disposition 使用 RFC 5987 编码文件名，正确转义，杜绝注入
    ContentDisposition disposition =
        ContentDisposition.attachment()
            .filename(downloaded.filename(), StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(MediaType.parseMediaType(downloaded.contentType()))
        .body(new org.springframework.core.io.InputStreamResource(downloaded.in()));
  }
}
