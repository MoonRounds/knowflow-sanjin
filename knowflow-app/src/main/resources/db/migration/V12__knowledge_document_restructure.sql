-- V12__knowledge_document_restructure.sql
-- 落地 ADR 0007：knowledge_item 单归属重构。
--   1) 四表 RENAME（MySQL 自动跟随指向旧表的外键）
--   2) knowledge_document 加 kb_id（回填后 NOT NULL + FK + 索引）、软删统一 deleted（从 status 回填后删 status）
--   3) DROP knowledge_base_item（多对多关联表）
--   4) tag 补 deleted，唯一约束仿 knowledge_base（V4 模式）
--   5) knowledge_candidate KB 归属 CSV → 单值
-- 回填语义见 docs/plans/v1.5-phase-01-plan.md G6/G7/G8/G11。

-- ---------- 1. 表改名 ----------
RENAME TABLE knowledge_item TO knowledge_document;

-- ---------- 2. knowledge_document：kb_id ----------
ALTER TABLE knowledge_document
    ADD COLUMN kb_id BIGINT NULL AFTER owner_id,
    ADD INDEX idx_kdoc_kb (kb_id);

-- 主归属：取每文档第一条未软删关联（按 KB id 最小，确定性）
UPDATE knowledge_document d
JOIN (
    SELECT knowledge_item_id AS item_id, MIN(knowledge_base_id) AS kb_id
    FROM knowledge_base_item
    WHERE deleted = 0
    GROUP BY knowledge_item_id
) t ON t.item_id = d.id
SET d.kb_id = t.kb_id;

-- 兜底：无未软删关联的文档取任一条（含已软删），保证不产生孤儿
UPDATE knowledge_document d
JOIN (
    SELECT knowledge_item_id AS item_id, MIN(knowledge_base_id) AS kb_id
    FROM knowledge_base_item
    GROUP BY knowledge_item_id
) t ON t.item_id = d.id
SET d.kb_id = COALESCE(d.kb_id, t.kb_id);

-- 仍为 NULL 的孤儿文档：NOT NULL 约束报错暴露（G6 自守卫），迁移失败需人工核对
ALTER TABLE knowledge_document
    MODIFY COLUMN kb_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_kdoc_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id);

-- ---------- 3. knowledge_document：软删统一 deleted（G7） ----------
ALTER TABLE knowledge_document
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;

UPDATE knowledge_document SET deleted = CASE WHEN status = 'DELETED' THEN 1 ELSE 0 END;

ALTER TABLE knowledge_document
    DROP INDEX idx_kitem_owner_status,
    DROP COLUMN status,
    RENAME INDEX idx_kitem_owner_created TO idx_kdoc_owner_created,
    ADD INDEX idx_kdoc_owner_deleted (owner_id, deleted);

-- 约束改名（编码旧表概念的名称同步为新名）；先删 FK 再删其支撑的唯一索引
ALTER TABLE knowledge_document
    DROP FOREIGN KEY fk_kitem_candidate,
    DROP FOREIGN KEY fk_kitem_owner;

ALTER TABLE knowledge_document
    DROP INDEX uk_kitem_candidate;

ALTER TABLE knowledge_document
    ADD CONSTRAINT fk_kdoc_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    ADD CONSTRAINT fk_kdoc_candidate FOREIGN KEY (candidate_id) REFERENCES knowledge_candidate (id) ON DELETE SET NULL,
    ADD CONSTRAINT uk_kdoc_candidate UNIQUE (candidate_id);

-- ---------- 4. knowledge_chunk → knowledge_document_chunk ----------
RENAME TABLE knowledge_chunk TO knowledge_document_chunk;

ALTER TABLE knowledge_document_chunk
    DROP FOREIGN KEY fk_kchunk_item;

ALTER TABLE knowledge_document_chunk
    RENAME COLUMN knowledge_item_id TO knowledge_document_id,
    RENAME INDEX idx_kchunk_item_version TO idx_kchunk_document_version;

ALTER TABLE knowledge_document_chunk
    DROP INDEX uk_kchunk_version_index;

ALTER TABLE knowledge_document_chunk
    ADD CONSTRAINT fk_kchunk_document FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document (id),
    ADD CONSTRAINT uk_kchunk_document_version_index UNIQUE (knowledge_document_id, content_version, chunk_index);

-- ---------- 5. knowledge_item_tag → knowledge_document_tag ----------
RENAME TABLE knowledge_item_tag TO knowledge_document_tag;

-- 先删全部 FK（idx_kit_tag 被 fk_kit_tag 占用，uk_kit_pair 被 fk_kit_item 占用），再改列
ALTER TABLE knowledge_document_tag
    DROP FOREIGN KEY fk_kit_item,
    DROP FOREIGN KEY fk_kit_tag,
    DROP FOREIGN KEY fk_kit_owner;

ALTER TABLE knowledge_document_tag
    RENAME COLUMN knowledge_item_id TO knowledge_document_id;

ALTER TABLE knowledge_document_tag
    DROP INDEX idx_kit_tag,
    DROP INDEX uk_kit_pair;

-- 先建索引再建 FK，让 MySQL 复用显式索引
ALTER TABLE knowledge_document_tag
    ADD INDEX idx_kit_document_tag (tag_id, deleted),
    ADD CONSTRAINT uk_kit_document_tag UNIQUE (knowledge_document_id, tag_id),
    ADD CONSTRAINT fk_kit_document FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document (id),
    ADD CONSTRAINT fk_kit_tag FOREIGN KEY (tag_id) REFERENCES tag (id),
    ADD CONSTRAINT fk_kit_owner FOREIGN KEY (owner_id) REFERENCES app_user (id);

-- ---------- 6. DROP knowledge_base_item（多对多关联表；kb_id 已回填） ----------
DROP TABLE knowledge_base_item;

-- ---------- 7. tag：补 deleted + 唯一约束调整（G11，仿 knowledge_base V4 模式） ----------
-- 先删 FK 再删其支撑的唯一索引（uk_tag_owner_name 被 fk_tag_owner 占用）
ALTER TABLE tag
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;

ALTER TABLE tag
    ADD COLUMN active_normalized_name VARCHAR(100)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN normalized_name ELSE NULL END) STORED;

ALTER TABLE tag
    DROP FOREIGN KEY fk_tag_owner;

ALTER TABLE tag
    DROP INDEX uk_tag_owner_name;

ALTER TABLE tag
    ADD CONSTRAINT fk_tag_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    ADD CONSTRAINT uk_tag_owner_active_normalized_name UNIQUE (owner_id, active_normalized_name);

-- ---------- 8. knowledge_candidate：KB 归属 CSV → 单值（G8） ----------
ALTER TABLE knowledge_candidate
    CHANGE COLUMN ai_knowledge_base_ids ai_knowledge_base_id VARCHAR(500) NULL,
    CHANGE COLUMN draft_knowledge_base_ids draft_knowledge_base_id VARCHAR(500) NULL;

-- 取 CSV 首个元素；空串/空白 → NULL（单值可空，必填约束在应用层确认时执行）
UPDATE knowledge_candidate
SET ai_knowledge_base_id = CASE
        WHEN ai_knowledge_base_id IS NULL OR TRIM(ai_knowledge_base_id) = '' THEN NULL
        ELSE TRIM(SUBSTRING_INDEX(ai_knowledge_base_id, ',', 1))
    END,
    draft_knowledge_base_id = CASE
        WHEN draft_knowledge_base_id IS NULL OR TRIM(draft_knowledge_base_id) = '' THEN NULL
        ELSE TRIM(SUBSTRING_INDEX(draft_knowledge_base_id, ',', 1))
    END;

-- ---------- 9. file_metadata：knowledge_item_id → knowledge_document_id ----------
ALTER TABLE file_metadata
    DROP FOREIGN KEY fk_fmeta_item;

ALTER TABLE file_metadata
    RENAME COLUMN knowledge_item_id TO knowledge_document_id;

ALTER TABLE file_metadata
    DROP INDEX uk_fmeta_item;

-- 先建唯一索引再建 FK，让 FK 复用该索引
ALTER TABLE file_metadata
    ADD CONSTRAINT uk_fmeta_document UNIQUE (knowledge_document_id),
    ADD CONSTRAINT fk_fmeta_document FOREIGN KEY (knowledge_document_id) REFERENCES knowledge_document (id);
