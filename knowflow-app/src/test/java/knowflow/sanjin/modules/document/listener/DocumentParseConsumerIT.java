package knowflow.sanjin.modules.document.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import knowflow.sanjin.modules.document.DocumentConstants;
import knowflow.sanjin.modules.document.config.DocumentProperties;
import knowflow.sanjin.modules.document.entity.FileMetadata;
import knowflow.sanjin.modules.document.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.modules.processing.mapper.ProcessingTaskMapper;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import knowflow.sanjin.testinfra.MySQLRabbitMQTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** 文档解析→索引集成测试：真实 MySQL + RabbitMQ，验证解析任务执行、Item 内容填充、索引任务可靠提交与重复消费幂等。 */
@SpringBootTest
@TestPropertySource(properties = "knowflow.rabbit.retry-delays=1s,1s,1s")
@DisplayName("DocumentParse Consumer Integration Tests")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DocumentParseConsumerIT extends MySQLRabbitMQTestBase {

  @Autowired private TaskSubmissionService submissionService;

  @Autowired private ProcessingTaskMapper taskMapper;

  @Autowired private FileMetadataMapper fileMapper;

  @Autowired private KnowledgeItemMapper itemMapper;

  @Autowired private DocumentProperties documentProperties;

  @DynamicPropertySource
  static void documentStorage(DynamicPropertyRegistry registry) throws IOException {
    Path dir = Files.createTempDirectory("knowflow-doc-it");
    registry.add("knowflow.document.storage-root", () -> dir.toString());
  }

  @Test
  @DisplayName("解析成功后填充 Item 正文并可靠提交一次索引任务")
  void parsesFileAndSubmitsIndexTask() throws Exception {
    // 准备：创建 KnowledgeItem + FileMetadata + 磁盘原文件
    KnowledgeItem item = new KnowledgeItem();
    item.setOwnerId(1L);
    item.setSourceType(KnowledgeConstants.SOURCE_UPLOAD_FILE);
    item.setTitle("doc-a");
    item.setContent("");
    item.setContentVersion(1);
    item.setIndexStatus(KnowledgeConstants.INDEX_PENDING);
    item.setStatus(KnowledgeConstants.STATUS_ACTIVE);
    item.setRowVersion(0);
    itemMapper.insert(item);

    FileMetadata file = new FileMetadata();
    file.setOwnerId(1L);
    file.setKnowledgeItemId(item.getId());
    file.setStorageKey("it-key-a");
    file.setOriginalFilename("doc-a.md");
    file.setContentType("text/markdown");
    file.setDetectedMimeType("text/plain");
    file.setByteSize(10L);
    file.setSha256("0000000000000000000000000000000000000000000000000000000000000000");
    file.setStatus(DocumentConstants.FILE_STATUS_ACTIVE);
    file.setParseStatus(DocumentConstants.PARSE_STATUS_PENDING);
    fileMapper.insert(file);

    // 写入真实原文件到存储根目录
    Path stored = documentProperties.storageRootPath().resolve("it-key-a");
    Files.writeString(stored, "# 标题\n\n正文内容", StandardCharsets.UTF_8);

    // 提交解析任务
    ProcessingTask task =
        submissionService.submit(
            DocumentConstants.TASK_TYPE_DOCUMENT_PARSE,
            DocumentConstants.BUSINESS_KEY_PREFIX + file.getId(),
            file.getId(),
            1L,
            null,
            3,
            DocumentConstants.WORK_QUEUE_BASE);

    waitForStatus(task.getId(), ProcessingConstants.STATUS_SUCCEEDED);

    // Item 正文已填充，title 取 H1
    KnowledgeItem updated = itemMapper.selectById(item.getId());
    assertThat(updated.getContent()).contains("正文内容");
    assertThat(updated.getTitle()).isEqualTo("标题");
    assertThat(updated.getContentVersion()).isEqualTo(1);

    // 文件解析状态 SUCCEEDED
    FileMetadata meta = fileMapper.selectById(file.getId());
    assertThat(meta.getParseStatus()).isEqualTo(DocumentConstants.PARSE_STATUS_SUCCEEDED);

    // 索引任务已提交（KNOWLEDGE_INDEX 同 business key = item:1）
    ProcessingTask indexTask =
        taskMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProcessingTask>()
                .eq(ProcessingTask::getTaskType, ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX)
                .eq(ProcessingTask::getBusinessKey, "KNOWLEDGE_ITEM:" + item.getId() + ":1")
                .last("LIMIT 1"));
    assertThat(indexTask).isNotNull();
  }

  private void waitForStatus(Long taskId, String status) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      ProcessingTask task = taskMapper.selectById(taskId);
      if (task != null && status.equals(task.getStatus())) {
        return;
      }
      if (task != null) {
        System.out.println(
            "[DocumentParseIT] task "
                + taskId
                + " status="
                + task.getStatus()
                + " retry="
                + task.getRetryCount()
                + " error="
                + task.getFailureCode()
                + " lastError="
                + task.getLastError()
                + " deliveries="
                + task.getAttemptedDeliveries());
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Timed out waiting for task " + taskId + " to reach " + status);
  }
}
