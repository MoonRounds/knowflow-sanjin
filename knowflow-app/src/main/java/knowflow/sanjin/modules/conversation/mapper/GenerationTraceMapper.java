package knowflow.sanjin.modules.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.conversation.entity.GenerationTrace;
import org.apache.ibatis.annotations.Mapper;

/** GenerationTrace Mapper；按 assistant_message_id 唯一关联。 */
@Mapper
public interface GenerationTraceMapper extends BaseMapper<GenerationTrace> {}
