package knowflow.sanjin.modules.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.file.entity.FileMetadata;
import knowflow.sanjin.modules.file.exception.RetryableFileException;
import knowflow.sanjin.modules.file.exception.TerminalFileException;
import knowflow.sanjin.modules.file.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.service.KnowledgeDocumentService;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import org.junit.jupiter.api.Test;

/** FileParsingService：失败场景映射为可区分 failureCode（原文件缺失 / 读取失败 / 解析失败）。 */
class FileParsingServiceTest {

  private static ProcessingTask task(long businessId) {
    ProcessingTask t = new ProcessingTask();
    t.setId(1L);
    t.setBusinessId(businessId);
    t.setOwnerId(1L);
    return t;
  }

  private static FileMetadata fileMeta() {
    FileMetadata file = new FileMetadata();
    file.setId(5L);
    file.setOwnerId(1L);
    file.setKnowledgeDocumentId(9L);
    file.setStorageKey("k");
    return file;
  }

  private static KnowledgeDocument item() {
    KnowledgeDocument item = new KnowledgeDocument();
    item.setId(9L);
    item.setOwnerId(1L);
    return item;
  }

  @Test
  void sourceFileMissingIsTerminalWithSpecificCode() {
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeDocumentMapper itemMapper = mock(KnowledgeDocumentMapper.class);
    when(fileMapper.selectById(5L)).thenReturn(fileMeta());
    when(itemMapper.selectById(9L)).thenReturn(item());
    LocalFileStore store = mock(LocalFileStore.class);
    when(store.missing("k")).thenReturn(true);
    FileStorageService storage = mock(FileStorageService.class);

    FileParsingService svc =
        new FileParsingService(
            fileMapper,
            itemMapper,
            mock(KnowledgeDocumentService.class),
            mock(FileParser.class),
            store,
            storage);

    assertThatThrownBy(() -> svc.execute(task(5L)))
        .isInstanceOf(TerminalFileException.class)
        .satisfies(
            e ->
                assertThat(((TerminalFileException) e).getFailureCode())
                    .isEqualTo(ErrorCode.FILE_STORED_MISSING));
  }

  @Test
  void readFailureIsRetryableWithSpecificCode() throws Exception {
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeDocumentMapper itemMapper = mock(KnowledgeDocumentMapper.class);
    when(fileMapper.selectById(5L)).thenReturn(fileMeta());
    when(itemMapper.selectById(9L)).thenReturn(item());
    LocalFileStore store = mock(LocalFileStore.class);
    when(store.missing("k")).thenReturn(false);
    FileStorageService storage = mock(FileStorageService.class);
    when(storage.read("k")).thenThrow(new IOException("disk"));

    FileParsingService svc =
        new FileParsingService(
            fileMapper,
            itemMapper,
            mock(KnowledgeDocumentService.class),
            mock(FileParser.class),
            store,
            storage);

    assertThatThrownBy(() -> svc.execute(task(5L)))
        .isInstanceOf(RetryableFileException.class)
        .satisfies(
            e ->
                assertThat(((RetryableFileException) e).getFailureCode())
                    .isEqualTo(ErrorCode.DOCUMENT_PARSE_READ_FAILED));
  }
}
