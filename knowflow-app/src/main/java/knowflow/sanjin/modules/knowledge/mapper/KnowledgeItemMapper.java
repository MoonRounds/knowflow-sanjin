package knowflow.sanjin.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeItemMapper extends BaseMapper<KnowledgeItem> {}
