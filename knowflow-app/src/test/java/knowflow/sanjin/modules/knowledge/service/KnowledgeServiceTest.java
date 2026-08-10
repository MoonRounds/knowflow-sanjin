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
import knowflow.sanjin.modules.knowledge.dto.UpdateManualNoteRequest;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import knowflow.sanjin.modules.knowledge.exception.KnowledgeItemVersionConflictException;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeBaseItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemMapper;
import knowflow.sanjin.modules.knowledge.mapper.KnowledgeItemTagMapper;
import knowflow.sanjin.modules.knowledge.mapper.TagMapper;
import knowflow.sanjin.modules.knowledgebase.service.KnowledgeBaseService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.service.TaskSubmissionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KnowledgeService 单元测试：乐观锁原子更新落败时抛版本冲突。 */
class KnowledgeServiceTest {

  @Test
  @DisplayName("should report a conflict when the atomic optimistic-lock update loses")
  void shouldRejectLostOptimisticLockUpdate() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "knowledge-test"),
        KnowledgeItem.class);
    KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
    KnowledgeBaseItemMapper kbItemMapper = mock(KnowledgeBaseItemMapper.class);
    KnowledgeItemTagMapper itemTagMapper = mock(KnowledgeItemTagMapper.class);
    TagMapper tagMapper = mock(TagMapper.class);
    KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    TaskSubmissionService taskSubmissionService = mock(TaskSubmissionService.class);

    KnowledgeItem current = new KnowledgeItem();
    current.setId(10L);
    current.setOwnerId(1L);
    current.setTitle("Note");
    current.setContent("body");
    current.setContentVersion(1);
    current.setIndexStatus("INDEXED");
    current.setStatus("ACTIVE");
    current.setRowVersion(3);
    when(itemMapper.selectOne(any(Wrapper.class))).thenReturn(current);
    // selectList for relation checks returns empty (no current relations)
    when(kbItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    when(itemTagMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    when(itemMapper.update(any(), any(Wrapper.class))).thenReturn(0);

    KnowledgeService service =
        new KnowledgeService(
            new CurrentOwnerProvider(),
            itemMapper,
            kbItemMapper,
            itemTagMapper,
            tagMapper,
            knowledgeBaseService,
            taskSubmissionService);

    UpdateManualNoteRequest request = new UpdateManualNoteRequest();
    request.setContent("new body");
    request.setKnowledgeBaseIds(List.of("5"));
    request.setRowVersion(3);
    when(knowledgeBaseService.getByIdAndOwner(eq(5L))).thenReturn(null);

    assertThatThrownBy(() -> service.updateManualNote(10L, request))
        .isInstanceOf(KnowledgeItemVersionConflictException.class);
  }
}
