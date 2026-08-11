package knowflow.sanjin.modules.extraction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.extraction.entity.KnowledgeExtractionTask;
import org.apache.ibatis.annotations.Mapper;

/** 提取任务快照 Mapper；幂等去重查询与任务状态联动（via processing_task_id）在此完成。 */
@Mapper
public interface KnowledgeExtractionTaskMapper extends BaseMapper<KnowledgeExtractionTask> {}
