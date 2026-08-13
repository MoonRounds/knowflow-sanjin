package knowflow.sanjin.modules.knowledge.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import knowflow.sanjin.modules.knowledge.dto.UpdateDocumentRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeDocumentVersionConflictException;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeDocumentTagMapper;
import knowflow.sanjin.modules.knowledge.mapper.TagMapper;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KnowledgeDocumentService 单元测试：乐观锁原子更新落败时抛版本冲突。 */
class KnowledgeServiceTest {

  @Test
  @DisplayName("should report a conflict when the atomic optimistic-lock update loses")
  void shouldRejectLostOptimisticLockUpdate() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "knowledge-test"),
        KnowledgeDocument.class);
    KnowledgeDocumentMapper itemMapper = mock(KnowledgeDocumentMapper.class);
    KnowledgeDocumentTagMapper itemTagMapper = mock(KnowledgeDocumentTagMapper.class);
    TagMapper tagMapper = mock(TagMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);

    KnowledgeDocument current = new KnowledgeDocument();
    current.setId(10L);
    current.setOwnerId(1L);
    current.setTitle("Note");
    current.setContent("body");
    current.setContentVersion(1);
    current.setIndexStatus("INDEXED");
    current.setDeleted(false);
    current.setRowVersion(3);
    when(itemMapper.selectOne(any(Wrapper.class))).thenReturn(current);
    // selectList for relation checks returns empty (no current relations)
    when(itemTagMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    when(itemMapper.update(any(), any(Wrapper.class))).thenReturn(0);

    KnowledgeDocumentService service =
        new KnowledgeDocumentService(
            new CurrentOwnerProvider(),
            itemMapper,
            itemTagMapper,
            tagMapper,
            knowledgeBaseService,
            taskSubmissionService,
            List.of());

    UpdateDocumentRequest request = new UpdateDocumentRequest();
    request.setContent("new body");
    request.setKnowledgeBaseId("5");
    request.setRowVersion(3);
    when(knowledgeBaseService.getByIdAndOwner(eq(5L))).thenReturn(null);

    assertThatThrownBy(() -> service.updateManualNote(10L, request))
        .isInstanceOf(KnowledgeDocumentVersionConflictException.class);
  }
}
