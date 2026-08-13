package knowflow.sanjin.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import knowflow.sanjin.modules.knowledge.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

  /** 每知识库活跃文档数（deleted=0 分组统计），供库列表页展示 documentCount（G24）。 */
  @Select(
      "SELECT kb_id AS kbId, COUNT(*) AS documentCount FROM knowledge_document"
          + " WHERE owner_id = #{ownerId} AND deleted = 0 GROUP BY kb_id")
  List<KbDocumentCount> selectActiveDocumentCountByKb(@Param("ownerId") long ownerId);

  /** 分组统计结果：kbId → 活跃文档数。 */
  record KbDocumentCount(Long kbId, Long documentCount) {}
}
