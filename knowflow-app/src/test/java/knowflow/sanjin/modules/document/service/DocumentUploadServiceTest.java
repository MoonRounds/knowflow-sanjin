package knowflow.sanjin.modules.document.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import knowflow.sanjin.modules.document.config.DocumentProperties;
import knowflow.sanjin.modules.document.entity.FileMetadata;
import knowflow.sanjin.modules.document.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.document.vo.FileUploadResponse;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.service.KnowledgeService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DocumentUploadService：去重返回已有、新建提交解析任务、软删恢复语义。 */
class DocumentUploadServiceTest {

  @TempDir Path tempDir;

  private DocumentProperties props() {
    DocumentProperties props = new DocumentProperties();
    props.setStorageRoot(tempDir.toString());
    return props;
  }

  @Test
  void duplicateContentReturnsExistingAndDoesNotCreateTask() throws Exception {
    initTableInfo();
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeService knowledgeService = mock(KnowledgeService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);
    MimeDetectionService mime = new MimeDetectionService();
    FileStorageService storage = new FileStorageService(props());
    LocalFileStore store = new LocalFileStore(props());

    FileMetadata existing = new FileMetadata();
    existing.setId(5L);
    existing.setOwnerId(1L);
    existing.setKnowledgeItemId(9L);
    existing.setStorageKey("stored-key");
    existing.setStatus("ACTIVE");
    // 模拟磁盘上已有原文件（修复路径不应触发）
    java.nio.file.Files.writeString(tempDir.resolve("stored-key"), "x");
    when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

    KnowledgeItem item = new KnowledgeItem();
    item.setId(9L);
    item.setOwnerId(1L);
    item.setTitle("已有");
    item.setStatus("ACTIVE");
    when(knowledgeService.getByIdAndOwner(9L)).thenReturn(item);

    DocumentUploadService service =
        new DocumentUploadService(
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
            "[\"1\"]");

    assertThat(response.isDuplicate()).isTrue();
    assertThat(response.getItem().getId()).isEqualTo("9");
    // 重复上传不创建任何新解析任务
    org.mockito.Mockito.verifyNoInteractions(taskSubmissionService);
  }

  @Test
  void newFileCreatesMetadataAndSubmitsParseTask() throws Exception {
    initTableInfo();
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeService knowledgeService = mock(KnowledgeService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);
    MimeDetectionService mime = new MimeDetectionService();
    FileStorageService storage = new FileStorageService(props());
    LocalFileStore store = new LocalFileStore(props());

    when(fileMapper.selectOne(any(Wrapper.class))).thenReturn(null);
    KnowledgeItem created = new KnowledgeItem();
    created.setId(11L);
    created.setOwnerId(1L);
    created.setTitle("NewDoc");
    created.setStatus("ACTIVE");
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

    DocumentUploadService service =
        new DocumentUploadService(
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
            "[\"1\"]");

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
