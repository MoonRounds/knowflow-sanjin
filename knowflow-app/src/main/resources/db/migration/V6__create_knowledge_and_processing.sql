-- V6__create_knowledge_and_processing.sql
-- KnowledgeItem / Tag / 关联 / KnowledgeChunk / ProcessingTask

-- KnowledgeItem：可索引知识条目；V1 仅 Manual Note 来源
CREATE TABLE knowledge_item
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id             BIGINT       NOT NULL,
    source_type          VARCHAR(30)  NOT NULL,
    title                VARCHAR(500) NOT NULL,
    summary              VARCHAR(2000) NULL,
    content              MEDIUMTEXT   NOT NULL,
    content_version      INT          NOT NULL DEFAULT 1,
    indexed_version      INT          NULL,
    index_status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    index_error_code     VARCHAR(50)  NULL,
    index_error_message  VARCHAR(1000) NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    row_version          INT          NOT NULL DEFAULT 0,
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_kitem_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_kitem_owner_status ON knowledge_item (owner_id, status);
CREATE INDEX idx_kitem_owner_created ON knowledge_item (owner_id, created_at);

-- Tag：Owner 级轻量实体；名称规范化（trim + 小写）去重
CREATE TABLE tag
(
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id         BIGINT       NOT NULL,
    name             VARCHAR(100) NOT NULL,
    normalized_name  VARCHAR(100) NOT NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_tag_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT uk_tag_owner_name
        UNIQUE (owner_id, normalized_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- KnowledgeItem 与 KnowledgeBase 多对多（软删关系：deleted=1）
CREATE TABLE knowledge_base_item
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    knowledge_base_id   BIGINT      NOT NULL,
    knowledge_item_id   BIGINT      NOT NULL,
    owner_id            BIGINT      NOT NULL,
    deleted             TINYINT     NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_kbi_kb
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id),
    CONSTRAINT fk_kbi_item
        FOREIGN KEY (knowledge_item_id) REFERENCES knowledge_item (id),
    CONSTRAINT fk_kbi_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT uk_kbi_pair
        UNIQUE (knowledge_base_id, knowledge_item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_kbi_item ON knowledge_base_item (knowledge_item_id, deleted);

-- KnowledgeItem 与 Tag 多对多（软删关系：deleted=1）
CREATE TABLE knowledge_item_tag
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    knowledge_item_id  BIGINT      NOT NULL,
    tag_id             BIGINT      NOT NULL,
    owner_id           BIGINT      NOT NULL,
    deleted            TINYINT     NOT NULL DEFAULT 0,
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_kit_item
        FOREIGN KEY (knowledge_item_id) REFERENCES knowledge_item (id),
    CONSTRAINT fk_kit_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id),
    CONSTRAINT fk_kit_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT uk_kit_pair
        UNIQUE (knowledge_item_id, tag_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_kit_tag ON knowledge_item_tag (tag_id, deleted);

-- KnowledgeChunk：规范 Chunk 正文与关系（MySQL 是正文事实源，Qdrant 不存正文）
CREATE TABLE knowledge_chunk
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    knowledge_item_id  BIGINT       NOT NULL,
    owner_id           BIGINT       NOT NULL,
    content_version    INT          NOT NULL,
    chunk_index        INT          NOT NULL,
    chunk_id           VARCHAR(64)  NOT NULL,
    content            MEDIUMTEXT   NOT NULL,
    heading_path       VARCHAR(500) NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_kchunk_item
        FOREIGN KEY (knowledge_item_id) REFERENCES knowledge_item (id),
    CONSTRAINT fk_kchunk_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT uk_kchunk_version_index
        UNIQUE (knowledge_item_id, content_version, chunk_index)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_kchunk_item_version ON knowledge_chunk (knowledge_item_id, content_version);

-- ProcessingTask：任务事实源兼轻量 Outbox；active_flag 生成列实现「活动状态唯一」
CREATE TABLE processing_task
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id               BIGINT       NOT NULL,
    task_type              VARCHAR(50)  NOT NULL,
    business_key           VARCHAR(200) NOT NULL,
    business_id            BIGINT       NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count            INT          NOT NULL DEFAULT 0,
    max_retries            INT          NOT NULL DEFAULT 3,
    failure_code           VARCHAR(50)  NULL,
    last_error             VARCHAR(1000) NULL,
    retry_of_task_id       BIGINT       NULL,
    payload                MEDIUMTEXT   NULL,
    attempted_deliveries   INT          NOT NULL DEFAULT 0,
    last_delivery_at       DATETIME(3)  NULL,
    started_at             DATETIME(3)  NULL,
    finished_at            DATETIME(3)  NULL,
    active_flag            TINYINT      GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING', 'PROCESSING') THEN 1 ELSE NULL END) STORED,
    created_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_ptask_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_ptask_retry_of
        FOREIGN KEY (retry_of_task_id) REFERENCES processing_task (id),
    CONSTRAINT uk_ptask_business_active
        UNIQUE (task_type, business_key, active_flag)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_ptask_status ON processing_task (status, updated_at);
CREATE INDEX idx_ptask_business ON processing_task (task_type, business_key);
CREATE INDEX idx_ptask_owner ON processing_task (owner_id);
