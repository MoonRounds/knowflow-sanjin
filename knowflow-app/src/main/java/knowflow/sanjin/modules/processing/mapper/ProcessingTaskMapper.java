package knowflow.sanjin.modules.processing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.processing.entity.ProcessingTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProcessingTaskMapper extends BaseMapper<ProcessingTask> {}
