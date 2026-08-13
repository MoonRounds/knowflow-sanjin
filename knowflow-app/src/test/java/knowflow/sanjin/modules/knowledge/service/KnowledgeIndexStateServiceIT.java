package knowflow.sanjin.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import knowflow.sanjin.modules.knowledge.KnowledgeConstants;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledgebase.dto.CreateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.processing.ProcessingConstants;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import knowflow.sanjin.testinfra.MySQLTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@DisplayName("Knowledge index state version projection integration tests")
class KnowledgeIndexStateServiceIT extends MySQLTestBase {

  @Autowired private KnowledgeIndexStateService stateService;
  @Autowired private KnowledgeDocumentMapper itemMapper;
  @Autowired private KnowledgeBaseService knowledgeBaseService;

  @Test
  @DisplayName("旧版本任务不得覆盖当前版本 INDEXED 状态")
  void staleTaskDoesNotOverwriteCurrentVersionState() {
    CreateKnowledgeBaseRequest kbReq = new CreateKnowledgeBaseRequest();
    kbReq.setName("State KB " + System.nanoTime());
    Long kbId = knowledgeBaseService.create(kbReq).getId();

    KnowledgeDocument item = new KnowledgeDocument();
    item.setOwnerId(1L);
    item.setKbId(kbId);
    item.setSourceType(KnowledgeConstants.SOURCE_MANUAL_NOTE);
    item.setTitle("versioned");
    item.setContent("body");
    item.setContentVersion(2);
    item.setIndexedVersion(2);
    item.setIndexStatus(KnowledgeConstants.INDEX_INDEXED);
    item.setDeleted(false);
    item.setRowVersion(0);
    itemMapper.insert(item);

    ProcessingTask stale = new ProcessingTask();
    stale.setTaskType(ProcessingConstants.TASK_TYPE_KNOWLEDGE_INDEX);
    stale.setBusinessId(item.getId());
    stale.setBusinessKey("KNOWLEDGE_DOCUMENT:" + item.getId() + ":1");
    stale.setOwnerId(1L);

    stateService.markProcessing(stale);
    stateService.markFailed(stale, "old-failure", "late v1");

    KnowledgeDocument unchanged = itemMapper.selectById(item.getId());
    assertThat(unchanged.getIndexStatus()).isEqualTo(KnowledgeConstants.INDEX_INDEXED);
    assertThat(unchanged.getIndexedVersion()).isEqualTo(2);
    assertThat(unchanged.getIndexErrorCode()).isNull();
  }
}
