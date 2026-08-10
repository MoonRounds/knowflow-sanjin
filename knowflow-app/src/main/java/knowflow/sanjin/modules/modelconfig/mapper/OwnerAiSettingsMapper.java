package knowflow.sanjin.modules.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.modelconfig.entity.OwnerAiSettings;
import org.apache.ibatis.annotations.Mapper;

/** Owner AI 设置（默认 Chat/Utility Model）的 MyBatis Mapper。 */
@Mapper
public interface OwnerAiSettingsMapper extends BaseMapper<OwnerAiSettings> {}
