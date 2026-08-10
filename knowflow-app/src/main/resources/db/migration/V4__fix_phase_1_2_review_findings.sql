-- 修复 Phase 1/2 Review findings。不改动已应用迁移的 checksum。

-- 只有 active 的 KnowledgeBase 参与名称唯一性。已删除行暴露为 NULL，
-- MySQL 唯一索引允许多个 NULL，因此可用生成列实现“仅未删除行唯一”。
ALTER TABLE knowledge_base
    DROP INDEX uk_kb_owner_normalized_name,
    ADD COLUMN active_normalized_name VARCHAR(200)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN normalized_name ELSE NULL END) STORED,
    ADD CONSTRAINT uk_kb_owner_active_normalized_name
        UNIQUE (owner_id, active_normalized_name);

-- 最新的 Utility 能力测试结果是挂在逻辑配置上的可变证据，
-- 而被测的 ModelConfigRevision 本身保持不可变。
ALTER TABLE model_config
    ADD COLUMN utility_tested_revision_id BIGINT NULL,
    ADD COLUMN utility_router_schema_valid TINYINT NULL,
    ADD COLUMN utility_candidate_schema_valid TINYINT NULL,
    ADD COLUMN utility_capability_tested_at DATETIME(3) NULL,
    ADD CONSTRAINT fk_mc_utility_tested_revision
        FOREIGN KEY (utility_tested_revision_id) REFERENCES model_config_revision (id);
