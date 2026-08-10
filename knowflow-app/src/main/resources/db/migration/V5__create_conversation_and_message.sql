-- V4__create_conversation_and_message.sql
-- Conversation：会话；ChatMessage：聊天消息（含 Assistant Generation Attempt）

-- 会话：Owner 下的聊天会话，含默认模型、active generation 指针、软删除与乐观锁
CREATE TABLE conversation
(
    id                             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id                       BIGINT       NOT NULL,
    title                          VARCHAR(200) NOT NULL DEFAULT '新会话',
    default_model_config_id        BIGINT       NULL,
    active_generation_message_id   BIGINT       NULL,
    deleted                        TINYINT      NOT NULL DEFAULT 0,
    row_version                    INT          NOT NULL DEFAULT 0,
    created_at                     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_conv_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    -- active_generation_message_id 指向 chat_message.id，但存在循环引用（chat_message.conversation_id -> conversation.id），
    -- 因此不建外键，应用层保证一致性，并通过事务内条件更新认领。
    CONSTRAINT fk_conv_default_model
        FOREIGN KEY (default_model_config_id) REFERENCES model_config (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_conv_owner_deleted ON conversation (owner_id, deleted);
CREATE INDEX idx_conv_owner_updated ON conversation (owner_id, updated_at);

-- 聊天消息：User 为轮起点；Assistant 为 Generation Attempt（GENERATING / COMPLETED / FAILED / CANCELLED）
CREATE TABLE chat_message
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id          BIGINT       NOT NULL,
    owner_id                 BIGINT       NOT NULL,
    role                     VARCHAR(20)  NOT NULL,
    sequence                 BIGINT       NOT NULL,
    content                  MEDIUMTEXT   NOT NULL,
    reply_to_message_id      BIGINT       NULL,
    client_message_id        VARCHAR(64)  NULL,
    generation_status        VARCHAR(20)  NULL,
    is_active                TINYINT      NOT NULL DEFAULT 0,
    model_config_id          BIGINT       NULL,
    revision_no              INT          NULL,
    model_name               VARCHAR(200) NULL,
    provider_name            VARCHAR(100) NULL,
    temperature              DOUBLE       NULL,
    max_output_tokens        INT          NULL,
    usage_prompt_tokens      INT          NULL,
    usage_completion_tokens  INT          NULL,
    usage_total_tokens       INT          NULL,
    error_code               VARCHAR(50)  NULL,
    created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_msg_conv
        FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT fk_msg_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_msg_reply_to
        FOREIGN KEY (reply_to_message_id) REFERENCES chat_message (id),
    CONSTRAINT uk_msg_seq
        UNIQUE (conversation_id, sequence),
    CONSTRAINT uk_msg_client
        UNIQUE (owner_id, client_message_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_msg_conv_seq ON chat_message (conversation_id, sequence);
CREATE INDEX idx_msg_owner_created ON chat_message (owner_id, created_at);
CREATE INDEX idx_msg_generation ON chat_message (generation_status, is_active);
