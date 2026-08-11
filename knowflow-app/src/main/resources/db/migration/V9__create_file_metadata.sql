-- V9__create_file_metadata.sql
-- 上传原文件元数据：与 KnowledgeItem 一对一；保存安全存储键、去重身份与来源文件名

CREATE TABLE file_metadata
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id              BIGINT       NOT NULL,
    knowledge_item_id     BIGINT       NOT NULL,
    storage_key           VARCHAR(255) NOT NULL,
    original_filename     VARCHAR(255) NOT NULL,
    content_type          VARCHAR(100) NOT NULL,
    detected_mime_type    VARCHAR(100) NOT NULL,
    byte_size             BIGINT       NOT NULL,
    sha256                CHAR(64)     NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- 文档解析状态：PENDING / PROCESSING / SUCCEEDED / FAILED（与处理任务状态区分）
    parse_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    parse_error_code      VARCHAR(50)  NULL,
    parse_error_message   VARCHAR(1000) NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_fmeta_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_fmeta_item
        FOREIGN KEY (knowledge_item_id) REFERENCES knowledge_item (id),
    CONSTRAINT uk_fmeta_item
        UNIQUE (knowledge_item_id),
    CONSTRAINT uk_fmeta_dedup
        UNIQUE (owner_id, detected_mime_type, sha256)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_fmeta_owner_status ON file_metadata (owner_id, status);
