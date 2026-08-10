package knowflow.sanjin.modules.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.conversation.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 会话 Mapper；行锁读取供生成事务串行化单 active Generation。 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

  /** 带行锁读取 conversation，用于生成事务串行化 active slot 与 sequence 分配。 */
  @Select(
      "SELECT * FROM conversation WHERE id = #{conversationId} AND owner_id = #{ownerId} AND deleted = 0 FOR UPDATE")
  Conversation selectConversationForUpdate(
      @Param("conversationId") Long conversationId, @Param("ownerId") Long ownerId);
}
