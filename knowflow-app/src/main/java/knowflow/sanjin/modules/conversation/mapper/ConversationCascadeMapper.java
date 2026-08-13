package knowflow.sanjin.modules.conversation.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话硬删除级联清理 Mapper：按 conversationId 一次删除消息、trace、提取任务与候选，并删除会话本身。
 *
 * <p>使用显式 SQL 而非实体 Lambda 删除，原因：级联要跨模块表（generation_trace / knowledge_candidate /
 * knowledge_extraction_task），且候选删除依赖子查询；全部在单个事务内执行。已确认候选的 KnowledgeItem 依赖 V10 迁移的 {@code ON DELETE
 * SET NULL} 保留沉淀数据。
 */
@Mapper
public interface ConversationCascadeMapper {

  /** 会话是否存在非终态（PENDING/PROCESSING）提取任务：存在则拒绝删除，避免消费端与删除并发竞态。 */
  @Select(
      "SELECT COUNT(*) FROM knowledge_extraction_task t "
          + "JOIN processing_task p ON t.processing_task_id = p.id "
          + "WHERE t.conversation_id = #{conversationId} "
          + "AND p.status IN ('PENDING', 'PROCESSING')")
  int countActiveExtractionTasks(@Param("conversationId") Long conversationId);

  @Delete("DELETE FROM generation_trace WHERE conversation_id = #{conversationId}")
  int deleteTraces(@Param("conversationId") Long conversationId);

  @Delete(
      "DELETE FROM knowledge_candidate "
          + "WHERE extraction_task_id IN "
          + "(SELECT id FROM knowledge_extraction_task WHERE conversation_id = #{conversationId})")
  int deleteCandidates(@Param("conversationId") Long conversationId);

  @Delete("DELETE FROM knowledge_extraction_task WHERE conversation_id = #{conversationId}")
  int deleteExtractionTasks(@Param("conversationId") Long conversationId);

  @Delete("DELETE FROM chat_message WHERE conversation_id = #{conversationId}")
  int deleteMessages(@Param("conversationId") Long conversationId);

  @Delete("DELETE FROM conversation WHERE id = #{conversationId}")
  int deleteConversation(@Param("conversationId") Long conversationId);
}
