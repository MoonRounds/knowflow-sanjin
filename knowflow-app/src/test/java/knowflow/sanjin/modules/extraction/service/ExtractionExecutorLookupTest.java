package knowflow.sanjin.modules.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import knowflow.sanjin.modules.conversation.service.ConversationService;
import knowflow.sanjin.modules.extraction.config.ExtractionProperties;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import knowflow.sanjin.modules.extraction.exception.TerminalExtractionException;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeCandidateMapper;
import knowflow.sanjin.modules.extraction.mapper.KnowledgeExtractionTaskMapper;
import knowflow.sanjin.modules.knowledgebase.mapper.KnowledgeBaseMapper;
import knowflow.sanjin.modules.modelconfig.service.ModelClientFactory;
import knowflow.sanjin.modules.modelconfig.service.ModelConfigService;
import knowflow.sanjin.modules.owner.service.CurrentOwnerProvider;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ExtractionExecutor 快照查找回归测试：F1（按 processing_task_id 匹配，而非 businessId/cutoff）。 */
class ExtractionExecutorLookupTest {

  private static final long OWNER_ID = 1L;

  private KnowledgeExtractionTaskMapper taskMapper;
  private KnowledgeBaseMapper kbMapper;
  private ConversationService conversationService;
  private ExtractionExecutor executor;
  private KnowledgeExtractionTask snapshot;

  @BeforeEach
  void setUp() {
    // 纯单测环境需初始化 MyBatis-Plus 元数据，否则 LambdaQueryWrapper 解析列名失败
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "lookup-test"),
        KnowledgeExtractionTask.class);

    taskMapper = mock(KnowledgeExtractionTaskMapper.class);
    kbMapper = mock(KnowledgeBaseMapper.class);
    conversationService = mock(ConversationService.class);
    CurrentOwnerProvider ownerProvider = mock(CurrentOwnerProvider.class);
    when(ownerProvider.getCurrentOwnerId()).thenReturn(OWNER_ID);
    // 无启用知识库 → execute 直接返回 0，不触发模型调用（聚焦快照查找路径）
    when(kbMapper.selectList(any())).thenReturn(List.of());

    executor =
        new ExtractionExecutor(
            ownerProvider,
            taskMapper,
            mock(KnowledgeCandidateMapper.class),
            kbMapper,
            conversationService,
            mock(ModelClientFactory.class),
            mock(ModelConfigService.class),
            new ExtractionProperties());

    snapshot = new KnowledgeExtractionTask();
    snapshot.setId(55L);
    snapshot.setOwnerId(OWNER_ID);
    snapshot.setConversationId(1L);
    snapshot.setCutoffMessageId(10L);
    snapshot.setProcessingTaskId(100L);
  }

  @Test
  @DisplayName("F1: should look up snapshot by processing_task_id, not businessId/cutoff")
  void shouldLookupSnapshotByProcessingTaskId() {
    ProcessingTask task = new ProcessingTask();
    task.setId(100L); // processing_task.id
    task.setBusinessId(10L); // cutoffMessageId（与快照自增 id 55 无关）
    when(taskMapper.selectOne(any())).thenReturn(snapshot);
    when(conversationService.loadAllTurnsUpTo(any(), anyLong(), any())).thenReturn(List.of());

    executor.executeWithLookup(task);

    @SuppressWarnings({"rawtypes", "unchecked"})
    ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper> wCap =
        ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
    verify(taskMapper).selectOne(wCap.capture());
    String sqlSegment = wCap.getValue().getSqlSegment();
    // 快照按 processing_task_id 匹配（而非业务键 businessId/cutoff）；参数值经占位符绑定，此处校验列名即可
    assertThat(sqlSegment).contains("processing_task_id");
    assertThat(sqlSegment).doesNotContain("cutoff");
    // F2: 快照的 cutoff 必须传给消息加载
    ArgumentCaptor<Long> cutoffCap = ArgumentCaptor.forClass(Long.class);
    verify(conversationService).loadAllTurnsUpTo(any(), anyLong(), cutoffCap.capture());
    assertThat(cutoffCap.getValue()).isEqualTo(10L);
  }

  @Test
  @DisplayName("F1: should fail terminal when snapshot is not found by processing_task_id")
  void shouldFailWhenSnapshotMissing() {
    ProcessingTask task = new ProcessingTask();
    task.setId(999L);
    task.setBusinessId(10L);
    when(taskMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> executor.executeWithLookup(task))
        .isInstanceOf(TerminalExtractionException.class);
  }

  @Test
  @DisplayName("F2: should pass snapshot cutoff to the turn loader")
  void shouldPassCutoffToTurnLoader() {
    ProcessingTask task = new ProcessingTask();
    task.setId(100L);
    task.setBusinessId(10L);
    when(taskMapper.selectOne(any())).thenReturn(snapshot);
    when(conversationService.loadAllTurnsUpTo(any(), anyLong(), any()))
        .thenReturn(List.of(new ChatMessage()));

    executor.executeWithLookup(task);

    ArgumentCaptor<Long> cutoffCap = ArgumentCaptor.forClass(Long.class);
    verify(conversationService).loadAllTurnsUpTo(any(), anyLong(), cutoffCap.capture());
    assertThat(cutoffCap.getValue()).isEqualTo(10L);
  }
}
