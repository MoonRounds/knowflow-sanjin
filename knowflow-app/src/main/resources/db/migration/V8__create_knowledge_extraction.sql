-- V8__create_knowledge_extraction.sql
-- KnowledgeExtractionTask 快照表 + KnowledgeCandidate 审核表

-- KnowledgeExtractionTask：异步提取任务快照；ProcessingTask 保存统一状态机，本表保存提取专属范围与身份
CREATE TABLE knowledge_extraction_task
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id              BIGINT       NOT NULL,
    conversation_id       BIGINT       NOT NULL,
    cutoff_message_id     BIGINT       NOT NULL,
    extraction_profile    VARCHAR(50)  NOT NULL,
    profile_version       INT          NOT NULL,
    utility_revision_id   BIGINT       NOT NULL,
    processing_task_id    BIGINT       NOT NULL,
    input_char_count      INT          NOT NULL,
    candidate_count       INT          NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_kext_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_kext_conv
        FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT fk_kext_cutoff
        FOREIGN KEY (cutoff_message_id) REFERENCES chat_message (id),
    CONSTRAINT fk_kext_task
        FOREIGN KEY (processing_task_id) REFERENCES processing_task (id),
    CONSTRAINT uk_kext_dedup
        UNIQUE (owner_id, conversation_id, cutoff_message_id, extraction_profile, profile_version, utility_revision_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_kext_owner_task ON knowledge_extraction_task (owner_id, processing_task_id);

-- KnowledgeCandidate：AI 原结果 + 用户编辑草稿 + 审核状态；确认后经 knowledge_item.candidate_id 关联 0..1 Item
CREATE TABLE knowledge_candidate
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id                BIGINT       NOT NULL,
    extraction_task_id      BIGINT       NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- AI 原始提取结果（不可变快照）
    ai_title                VARCHAR(500) NOT NULL,
    ai_summary              VARCHAR(2000) NULL,
    ai_content              MEDIUMTEXT   NOT NULL,
    ai_knowledge_base_ids   VARCHAR(500) NOT NULL,
    ai_tags                 VARCHAR(500) NOT NULL,
    ai_reason               VARCHAR(1000) NULL,
    -- 用户可编辑草稿（初始化复制 AI 原值；确认时完整读取）
    draft_title             VARCHAR(500) NOT NULL,
    draft_summary           VARCHAR(2000) NULL,
    draft_content           MEDIUMTEXT   NOT NULL,
    draft_knowledge_base_ids VARCHAR(500) NOT NULL,
    draft_tags              VARCHAR(500) NOT NULL,
    draft_updated_at        DATETIME(3)  NULL,
    rejected_at             DATETIME(3)  NULL,
    confirmed_at            DATETIME(3)  NULL,
    row_version             INT          NOT NULL DEFAULT 0,
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_kcand_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_kcand_task
        FOREIGN KEY (extraction_task_id) REFERENCES knowledge_extraction_task (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_kcand_owner_status ON knowledge_candidate (owner_id, status, created_at);
CREATE INDEX idx_kcand_task ON knowledge_candidate (extraction_task_id);

-- 确认后唯一 Item 归属（0..1）：同一 Candidate 最多创建一个 Item，唯一约束抵御并发确认
ALTER TABLE knowledge_item
    ADD COLUMN candidate_id BIGINT NULL,
    ADD CONSTRAINT fk_kitem_candidate
        FOREIGN KEY (candidate_id) REFERENCES knowledge_candidate (id),
    ADD CONSTRAINT uk_kitem_candidate
        UNIQUE (candidate_id);
