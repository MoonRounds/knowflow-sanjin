package knowflow.sanjin.modules.embeddingconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.embeddingconfig.entity.EmbeddingConfig;
import org.apache.ibatis.annotations.Mapper;

/** 系统级向量模型配置单例行的 MyBatis Mapper。 */
@Mapper
public interface EmbeddingConfigMapper extends BaseMapper<EmbeddingConfig> {}
