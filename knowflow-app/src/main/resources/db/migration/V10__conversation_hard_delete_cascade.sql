-- V10__conversation_hard_delete_cascade.sql
-- 会话硬删除级联：删除会话时消息与提取产物一并清除，已沉淀的 KnowledgeItem 保留。

-- chat_message 自引用 reply_to 改为 ON DELETE CASCADE：同一会话消息一次 DELETE 不再违反自引用外键。
ALTER TABLE chat_message DROP FOREIGN KEY fk_msg_reply_to;
ALTER TABLE chat_message
    ADD CONSTRAINT fk_msg_reply_to
        FOREIGN KEY (reply_to_message_id) REFERENCES chat_message (id)
        ON DELETE CASCADE;

-- knowledge_item.candidate_id 改为 ON DELETE SET NULL：候选随会话删除时，其确认沉淀的 Item 保留，仅解除来源关联。
ALTER TABLE knowledge_item DROP FOREIGN KEY fk_kitem_candidate;
ALTER TABLE knowledge_item
    ADD CONSTRAINT fk_kitem_candidate
        FOREIGN KEY (candidate_id) REFERENCES knowledge_candidate (id)
        ON DELETE SET NULL;
