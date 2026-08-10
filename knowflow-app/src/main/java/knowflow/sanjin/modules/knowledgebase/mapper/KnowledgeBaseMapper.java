package knowflow.sanjin.modules.knowledgebase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.knowledgebase.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/** KnowledgeBase 实体 Mapper；全部查询按 owner_id + deleted 过滤，名称唯一由数据库约束兜底。 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {}
