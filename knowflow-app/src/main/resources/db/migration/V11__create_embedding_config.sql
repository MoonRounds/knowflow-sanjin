-- V11__create_embedding_config.sql
-- EmbeddingConfig：系统级单一当前向量模型配置（id=1 单例，System Owner）。
-- 不进入 Chat ModelConfig 页面；dimension 由测试自动探测写入。API Key 加密存储、只回显掩码。

CREATE TABLE embedding_config
(
    id                         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id                   BIGINT       NOT NULL,
    base_url                   VARCHAR(500) NOT NULL,
    model_name                 VARCHAR(200) NOT NULL,
    encrypted_api_key          VARCHAR(500) NOT NULL,
    api_key_encryption_version INT          NOT NULL,
    api_key_masked             VARCHAR(50)  NOT NULL,
    dimension                  INT          NOT NULL,
    row_version                INT          NOT NULL DEFAULT 0,
    created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_emb_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
