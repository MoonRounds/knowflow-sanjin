package knowflow.sanjin.modules.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import knowflow.sanjin.modules.conversation.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** 聊天消息 Mapper；游标分页、幂等去重与 generation 状态更新均通过条件查询在此完成。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {}
