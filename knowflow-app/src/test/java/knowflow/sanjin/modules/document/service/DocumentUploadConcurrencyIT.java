package knowflow.sanjin.modules.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import knowflow.sanjin.modules.document.config.DocumentProperties;
import knowflow.sanjin.modules.document.entity.FileMetadata;
import knowflow.sanjin.modules.document.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.document.vo.FileUploadResponse;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.processing.service.TaskPublisher;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@DisplayName("Document upload concurrency integration tests")
class DocumentUploadConcurrencyIT extends MySQLTestBase {

  @Autowired private DocumentUploadCoordinator coordinator;
  @Autowired private KnowledgeBaseService knowledgeBaseService;
  @Autowired private FileMetadataMapper fileMapper;
  @Autowired private KnowledgeItemMapper itemMapper;
  @Autowired private DocumentProperties properties;
  @MockitoBean private TaskPublisher taskPublisher;

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) throws Exception {
    Path dir = Files.createTempDirectory("knowflow-upload-concurrency");
    registry.add("knowflow.document.storage-root", () -> dir.toString());
  }

  @Test
  @DisplayName("并发上传相同内容返回同一 Item 且只创建一份元数据")
  void concurrentDuplicateReturnsWinner() throws Exception {
    CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
    request.setName("Concurrent Upload " + System.nanoTime());
    KnowledgeBase kb = knowledgeBaseService.create(request);
    byte[] content = "# Same\n\nconcurrent body".getBytes(StandardCharsets.UTF_8);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<FileUploadResponse> first = executor.submit(() -> upload(start, content, kb.getId()));
      Future<FileUploadResponse> second = executor.submit(() -> upload(start, content, kb.getId()));
      start.countDown();
      FileUploadResponse a = first.get();
      FileUploadResponse b = second.get();

      assertThat(a.getItem().getId()).isEqualTo(b.getItem().getId());
      assertThat(fileMapper.selectList(new LambdaQueryWrapper<FileMetadata>())).hasSize(1);
      assertThat(itemMapper.selectList(new LambdaQueryWrapper<KnowledgeItem>())).hasSize(1);
      assertThat(List.of(a.isDuplicate(), b.isDuplicate())).containsExactlyInAnyOrder(false, true);
    }
    try (var stored = Files.list(properties.storageRootPath())) {
      assertThat(stored.filter(Files::isRegularFile)).hasSize(1);
    }
  }

  private FileUploadResponse upload(CountDownLatch start, byte[] content, Long kbId)
      throws Exception {
    start.await();
    return coordinator.upload(
        "same.md", "text/markdown", new ByteArrayInputStream(content), "[\"" + kbId + "\"]");
  }
}
