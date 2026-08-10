-- V2__create_knowledge_base.sql
-- KnowledgeBase： logical knowledge domain; one Owner has many KnowledgeBases

CREATE TABLE knowledge_base
(
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id        BIGINT       NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL,
    description     TEXT         NULL,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    row_version     INT          NOT NULL DEFAULT 0,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_kb_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),

    -- Unique constraint: one Owner cannot have two active KnowledgeBases with the same normalized name
    CONSTRAINT uk_kb_owner_normalized_name
        UNIQUE (owner_id, normalized_name, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_kb_owner_id ON knowledge_base (owner_id);
