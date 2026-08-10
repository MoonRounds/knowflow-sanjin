-- Close Phase 1/2 review findings without changing checksums of already-applied migrations.

-- Only active KnowledgeBases participate in name uniqueness. Deleted rows expose NULL,
-- and MySQL permits multiple NULL values in a unique index.
ALTER TABLE knowledge_base
    DROP INDEX uk_kb_owner_normalized_name,
    ADD COLUMN active_normalized_name VARCHAR(200)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN normalized_name ELSE NULL END) STORED,
    ADD CONSTRAINT uk_kb_owner_active_normalized_name
        UNIQUE (owner_id, active_normalized_name);

-- The latest Utility capability result is mutable evidence attached to the logical config,
-- while the tested ModelConfigRevision itself remains immutable.
ALTER TABLE model_config
    ADD COLUMN utility_tested_revision_id BIGINT NULL,
    ADD COLUMN utility_router_schema_valid TINYINT NULL,
    ADD COLUMN utility_candidate_schema_valid TINYINT NULL,
    ADD COLUMN utility_capability_tested_at DATETIME(3) NULL,
    ADD CONSTRAINT fk_mc_utility_tested_revision
        FOREIGN KEY (utility_tested_revision_id) REFERENCES model_config_revision (id);
