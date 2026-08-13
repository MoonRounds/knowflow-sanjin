package knowflow.sanjin.modules.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import knowflow.sanjin.modules.file.config.FileProperties;
import knowflow.sanjin.modules.file.entity.FileMetadata;
import knowflow.sanjin.modules.file.exception.FileTooLargeException;
import knowflow.sanjin.modules.file.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.file.vo.FileUploadResponse;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.service.KnowledgeDocumentService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** FileUploadService：去重返回已有、新建提交解析任务、软删恢复语义。 */
class FileUploadServiceTest {

  @TempDir Path tempDir;

  private FileProperties props() {
    FileProperties props = new FileProperties();
    props.setStorageRoot(tempDir.toString());
    return props;
  }

  @Test
  void duplicateContentReturnsExistingAndDoesNotCreateTask() throws Exception {
    initTableInfo();
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeDocumentService knowledgeService = mock(KnowledgeDocumentService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);
    MimeDetectionService mime = new MimeDetectionService();
    FileStorageService storage = new FileStorageService(props());
    LocalFileStore store = new LocalFileStore(props());

    FileMetadata existing = new FileMetadata();
    existing.setId(5L);
    existing.setOwnerId(1L);
    existing.setKnowledgeDocumentId(9L);
    existing.setStorageKey("stored-key");
    existing.setStatus("ACTIVE");
    // 模拟磁盘上已有原文件（修复路径不应触发）
    java.nio.file.Files.writeString(tempDir.resolve("stored-key"), "x");
    when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

    KnowledgeDocument item = new KnowledgeDocument();
    item.setId(9L);
    item.setOwnerId(1L);
    item.setTitle("已有");
    item.setDeleted(false);
    when(knowledgeService.getByIdAndOwnerIncludingDeleted(9L)).thenReturn(item);

    FileUploadService service =
        new FileUploadService(
            fileMapper,
            new CurrentOwnerProvider(),
            props(),
            mime,
            storage,
            store,
            knowledgeService,
            taskSubmissionService);

    FileUploadResponse response =
        service.upload(
            "a.md",
            "text/markdown",
            new ByteArrayInputStream("# x\n".getBytes(StandardCharsets.UTF_8)),
            "1");

    assertThat(response.isDuplicate()).isTrue();
    assertThat(response.getItem().getId()).isEqualTo("9");
    // 重复上传不创建任何新解析任务
    org.mockito.Mockito.verifyNoInteractions(taskSubmissionService);
  }

  @Test
  void newFileCreatesMetadataAndSubmitsParseTask() throws Exception {
    initTableInfo();
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeDocumentService knowledgeService = mock(KnowledgeDocumentService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);
    MimeDetectionService mime = new MimeDetectionService();
    FileStorageService storage = new FileStorageService(props());
    LocalFileStore store = new LocalFileStore(props());

    when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    KnowledgeDocument created = new KnowledgeDocument();
    created.setId(11L);
    created.setOwnerId(1L);
    created.setTitle("NewDoc");
    created.setDeleted(false);
    when(knowledgeService.createUploadItem(any(), any())).thenReturn(created);
    // 模拟 MyBatis-Plus 插入时自动回填主键
    org.mockito.Mockito.doAnswer(
            inv -> {
              FileMetadata f = inv.getArgument(0);
              f.setId(42L);
              return 1;
            })
        .when(fileMapper)
        .insert(any(FileMetadata.class));

    FileUploadService service =
        new FileUploadService(
            fileMapper,
            new CurrentOwnerProvider(),
            props(),
            mime,
            storage,
            store,
            knowledgeService,
            taskSubmissionService);

    FileUploadResponse response =
        service.upload(
            "NewDoc.md",
            "text/markdown",
            new ByteArrayInputStream("# 新文档\n\n正文".getBytes(StandardCharsets.UTF_8)),
            "1");

    assertThat(response.isDuplicate()).isFalse();
    assertThat(response.getItem().getId()).isEqualTo("11");
    assertThat(response.getFile().getOriginalFilename()).isEqualTo("NewDoc.md");
    // 提交解析任务（ownerId 为 primitive long，用 anyLong 匹配）
    verify(taskSubmissionService)
        .submit(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyString());
    // 正式文件落盘
    assertThat(java.nio.file.Files.list(tempDir).count()).isGreaterThan(0);
  }

  @Test
  void deletedDuplicateRestoresItemWithKnowledgeBasesSelectedByCurrentUpload() throws Exception {
    initTableInfo();
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeDocumentService knowledgeService = mock(KnowledgeDocumentService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);
    MimeDetectionService mime = new MimeDetectionService();
    FileStorageService storage = new FileStorageService(props());
    LocalFileStore store = new LocalFileStore(props());

    FileMetadata existing = new FileMetadata();
    existing.setId(5L);
    existing.setOwnerId(1L);
    existing.setKnowledgeDocumentId(9L);
    existing.setStorageKey("deleted-key");
    existing.setStatus("DELETED");
    when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

    KnowledgeDocument deleted = new KnowledgeDocument();
    deleted.setId(9L);
    deleted.setOwnerId(1L);
    deleted.setTitle("已删除");
    deleted.setDeleted(true);
    KnowledgeDocument restored = new KnowledgeDocument();
    restored.setId(9L);
    restored.setOwnerId(1L);
    restored.setTitle("已恢复");
    restored.setDeleted(false);
    when(knowledgeService.getByIdAndOwnerIncludingDeleted(9L)).thenReturn(deleted, restored);
    when(knowledgeService.restoreUploadItem(9L, 1L)).thenReturn(restored);

    FileUploadService service =
        new FileUploadService(
            fileMapper,
            new CurrentOwnerProvider(),
            props(),
            mime,
            storage,
            store,
            knowledgeService,
            taskSubmissionService);

    FileUploadResponse response =
        service.upload(
            "restored.md",
            "text/markdown",
            new ByteArrayInputStream("# x\n".getBytes(StandardCharsets.UTF_8)),
            "1");

    assertThat(response.isDuplicate()).isFalse();
    assertThat(response.getItem().getId()).isEqualTo("9");
    verify(knowledgeService).restoreUploadItem(9L, 1L);
    verify(taskSubmissionService)
        .submit(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void oversizedFileRejectedWithoutLeavingTempOrCommittedFiles() throws Exception {
    initTableInfo();
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeDocumentService knowledgeService = mock(KnowledgeDocumentService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);
    MimeDetectionService mime = new MimeDetectionService();
    FileStorageService storage = new FileStorageService(props());
    LocalFileStore store = new LocalFileStore(props());

    FileProperties props = props();
    props.setMaxFileBytes(10);
    FileUploadService service =
        new FileUploadService(
            fileMapper,
            new CurrentOwnerProvider(),
            props,
            mime,
            storage,
            store,
            knowledgeService,
            taskSubmissionService);

    byte[] payload = new byte[11];
    java.util.Arrays.fill(payload, (byte) 'a');
    assertThatThrownBy(
            () -> service.upload("big.md", "text/markdown", new ByteArrayInputStream(payload), "1"))
        .isInstanceOf(FileTooLargeException.class);

    // 未创建任何任务，临时文件目录为空（无遗留临时文件）
    org.mockito.Mockito.verifyNoInteractions(taskSubmissionService);
    try (var stream = java.nio.file.Files.list(tempDir.resolve("tmp"))) {
      assertThat(stream).isEmpty();
    }
  }

  @Test
  void repairMissingFileDeletesOrphanOnDbFailure() throws Exception {
    initTableInfo();
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeDocumentService knowledgeService = mock(KnowledgeDocumentService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);
    MimeDetectionService mime = new MimeDetectionService();
    FileStorageService storage = new FileStorageService(props());
    LocalFileStore store = new LocalFileStore(props());

    FileMetadata existing = new FileMetadata();
    existing.setId(5L);
    existing.setOwnerId(1L);
    existing.setKnowledgeDocumentId(9L);
    existing.setStorageKey("stored-key");
    existing.setStatus("ACTIVE");
    // 磁盘文件缺失 → 进入修复路径；DB 更新存储键失败
    when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
    org.mockito.Mockito.doThrow(new RuntimeException("db down"))
        .when(fileMapper)
        .updateById(any(FileMetadata.class));

    KnowledgeDocument item = new KnowledgeDocument();
    item.setId(9L);
    item.setOwnerId(1L);
    item.setTitle("已有");
    item.setDeleted(false);
    when(knowledgeService.getByIdAndOwnerIncludingDeleted(9L)).thenReturn(item);

    FileUploadService service =
        new FileUploadService(
            fileMapper,
            new CurrentOwnerProvider(),
            props(),
            mime,
            storage,
            store,
            knowledgeService,
            taskSubmissionService);

    assertThatThrownBy(
            () ->
                service.upload(
                    "a.md",
                    "text/markdown",
                    new ByteArrayInputStream("# x\n".getBytes(StandardCharsets.UTF_8)),
                    "1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("db down");

    // 修复路径落盘的 newKey 原文件已被补偿删除，根目录无孤立文件
    try (var stream = java.nio.file.Files.list(tempDir)) {
      assertThat(stream.filter(p -> java.nio.file.Files.isRegularFile(p))).isEmpty();
    }
  }

  private static boolean tableInfoInitialized = false;

  private static void initTableInfo() {
    if (tableInfoInitialized) {
      return;
    }
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "document-test"),
        FileMetadata.class);
    tableInfoInitialized = true;
  }
}
