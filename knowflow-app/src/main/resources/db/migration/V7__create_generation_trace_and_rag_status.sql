-- V7__create_generation_trace_and_rag_status.sql
-- RAG trace 与消息 RAG 状态（Phase 6）

-- chat_message 增加轻量 RAG 状态列，便于历史列表廉价读取；完整来源在 generation_trace。
ALTER TABLE chat_message
    ADD COLUMN rag_status VARCHAR(20) NULL;

-- Generation Trace：与 assistant message 1:1。sources 以 JSON 快照保存（含 cited 标记），
-- router/retrieval 为诊断信息；不保存完整 Prompt 或私人正文。
CREATE TABLE generation_trace
(
    id                    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    assistant_message_id  BIGINT      NOT NULL,
    conversation_id       BIGINT      NOT NULL,
    owner_id              BIGINT      NOT NULL,
    rag_status            VARCHAR(20) NULL,
    sources_json          MEDIUMTEXT  NULL,
    router_json           MEDIUMTEXT  NULL,
    retrieval_json        MEDIUMTEXT  NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_gtrace_msg
        FOREIGN KEY (assistant_message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_gtrace_conv
        FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT fk_gtrace_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT uk_gtrace_message
        UNIQUE (assistant_message_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_gtrace_conv_created ON generation_trace (conversation_id, created_at);
