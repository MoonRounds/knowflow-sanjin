package knowflow.sanjin.modules.knowledgebase.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import knowflow.sanjin.modules.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import knowflow.sanjin.modules.knowledgebase.exception.KnowledgeBaseVersionConflictException;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KnowledgeBaseService 单元测试：乐观锁原子更新落败时抛版本冲突。 */
class KnowledgeBaseServiceTest {

  @Test
  @DisplayName("should report a conflict when the atomic optimistic-lock update loses")
  void shouldRejectLostOptimisticLockUpdate() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "knowledge-base-test"),
        KnowledgeBase.class);
    KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
    KnowledgeBase current = new KnowledgeBase();
    current.setId(10L);
    current.setOwnerId(1L);
    current.setDisplayName("Notes");
    current.setNormalizedName("notes");
    current.setEnabled(true);
    current.setDeleted(false);
    current.setRowVersion(3);
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(current);
    when(mapper.update(any(), any(Wrapper.class))).thenReturn(0);

    KnowledgeBaseService service =
        new KnowledgeBaseService(
            new CurrentOwnerProvider(),
            mapper,
            mock(org.springframework.jdbc.core.JdbcTemplate.class));
    UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
    request.setDescription("concurrent edit");
    request.setRowVersion(3);

    assertThatThrownBy(() -> service.update(10L, request))
        .isInstanceOf(KnowledgeBaseVersionConflictException.class);
  }
}
