package knowflow.sanjin.modules.extraction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.extraction.entity.KnowledgeCandidate;
import org.apache.ibatis.annotations.Mapper;

/** 知识候选 Mapper；状态迁移与并发版本保护通过条件更新完成。 */
@Mapper
public interface KnowledgeCandidateMapper extends BaseMapper<KnowledgeCandidate> {}
