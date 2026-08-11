package knowflow.sanjin.modules.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import knowflow.sanjin.common.error.ErrorCode;
import knowflow.sanjin.modules.document.entity.FileMetadata;
import knowflow.sanjin.modules.document.exception.RetryableDocumentException;
import knowflow.sanjin.modules.document.exception.TerminalDocumentException;
import knowflow.sanjin.modules.document.mapper.FileMetadataMapper;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.service.KnowledgeService;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import org.junit.jupiter.api.Test;

/** DocumentParsingService：失败场景映射为可区分 failureCode（原文件缺失 / 读取失败 / 解析失败）。 */
class DocumentParsingServiceTest {

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
    file.setKnowledgeItemId(9L);
    file.setStorageKey("k");
    return file;
  }

  private static KnowledgeItem item() {
    KnowledgeItem item = new KnowledgeItem();
    item.setId(9L);
    item.setOwnerId(1L);
    return item;
  }

  @Test
  void sourceFileMissingIsTerminalWithSpecificCode() {
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
    when(fileMapper.selectById(5L)).thenReturn(fileMeta());
    when(itemMapper.selectById(9L)).thenReturn(item());
    LocalFileStore store = mock(LocalFileStore.class);
    when(store.missing("k")).thenReturn(true);
    FileStorageService storage = mock(FileStorageService.class);

    DocumentParsingService svc =
        new DocumentParsingService(
            fileMapper,
            itemMapper,
            mock(KnowledgeService.class),
            mock(DocumentParser.class),
            store,
            storage);

    assertThatThrownBy(() -> svc.execute(task(5L)))
        .isInstanceOf(TerminalDocumentException.class)
        .satisfies(
            e ->
                assertThat(((TerminalDocumentException) e).getFailureCode())
                    .isEqualTo(ErrorCode.FILE_STORED_MISSING));
  }

  @Test
  void readFailureIsRetryableWithSpecificCode() throws Exception {
    FileMetadataMapper fileMapper = mock(FileMetadataMapper.class);
    KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
    when(fileMapper.selectById(5L)).thenReturn(fileMeta());
    when(itemMapper.selectById(9L)).thenReturn(item());
    LocalFileStore store = mock(LocalFileStore.class);
    when(store.missing("k")).thenReturn(false);
    FileStorageService storage = mock(FileStorageService.class);
    when(storage.read("k")).thenThrow(new IOException("disk"));

    DocumentParsingService svc =
        new DocumentParsingService(
            fileMapper,
            itemMapper,
            mock(KnowledgeService.class),
            mock(DocumentParser.class),
            store,
            storage);

    assertThatThrownBy(() -> svc.execute(task(5L)))
        .isInstanceOf(RetryableDocumentException.class)
        .satisfies(
            e ->
                assertThat(((RetryableDocumentException) e).getFailureCode())
                    .isEqualTo(ErrorCode.DOCUMENT_PARSE_READ_FAILED));
  }
}
