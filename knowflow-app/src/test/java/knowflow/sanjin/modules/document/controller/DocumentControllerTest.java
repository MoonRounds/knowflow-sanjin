package knowflow.sanjin.modules.document.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import knowflow.sanjin.common.exception.GlobalExceptionHandler;
import knowflow.sanjin.modules.document.exception.FileMetadataNotFoundException;
import knowflow.sanjin.modules.document.exception.StoredFileMissingException;
import knowflow.sanjin.modules.document.service.DocumentUploadCoordinator;
import knowflow.sanjin.modules.document.service.DocumentUploadService;
import knowflow.sanjin.modules.document.vo.FileMetadataResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** DocumentController 单元测试：下载 owner 校验/缺失、multipart 超限映射、字符串 ID 序列化。 */
class DocumentControllerTest {

  private MockMvc mvc;
  private DocumentUploadService service;

  /** 抛 MultipartException 的载体，用于验证 GlobalExceptionHandler 对解析层超限的映射。 */
  @RestController
  static class MultipartOverflowProbe {
    @GetMapping("/probe/multipart-overflow")
    void probe() {
      throw new MaxUploadSizeExceededException(1024);
    }
  }

  @BeforeEach
  void setUp() {
    service = mock(DocumentUploadService.class);
    DocumentUploadCoordinator coordinator = mock(DocumentUploadCoordinator.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new DocumentController(service, coordinator), new MultipartOverflowProbe())
            .addPlaceholderValue("knowflow.api.base-path", "/api/v1")
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("multipart 解析层超限映射为 422 + FILE_TOO_LARGE")
  void mapsMultipartTooLargeToProblemDetails() throws Exception {
    mvc.perform(get("/probe/multipart-overflow"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("文件超过大小限制"));
  }

  @Test
  @DisplayName("下载返回 owner 校验后的文件元数据，BIGINT id 序列化为字符串")
  void downloadSerializesStringId() throws Exception {
    FileMetadataResponse file = new FileMetadataResponse();
    file.setId("42");
    file.setKnowledgeItemId("7");
    file.setOriginalFilename("doc.md");
    when(service.getById(42L)).thenReturn(file);

    mvc.perform(get("/api/v1/files/{id}", "42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("42"))
        .andExpect(jsonPath("$.knowledgeItemId").value("7"))
        .andExpect(jsonPath("$.originalFilename").value("doc.md"));
  }

  @Test
  @DisplayName("下载缺失文件返回 500 + FILE_STORED_MISSING")
  void downloadMissingFileReturnsStoredMissing() throws Exception {
    when(service.download(anyLong())).thenThrow(new StoredFileMissingException("原文件缺失，存储键=x"));
    mvc.perform(get("/api/v1/files/1/download"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.errorCode").value("原文件缺失"));
  }

  @Test
  @DisplayName("按 id 查询不存在的文件返回 404 + DOCUMENT_FILE_NOT_FOUND")
  void getMissingFileReturnsNotFound() throws Exception {
    when(service.getById(999L)).thenThrow(new FileMetadataNotFoundException(999L));
    mvc.perform(get("/api/v1/files/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("文件元数据不存在"));
  }
}
