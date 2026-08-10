-- V3__create_model_config.sql
-- ModelConfig：逻辑配置；ModelConfigRevision：不可变具体参数；OwnerAiSettings：Owner 默认模型选择

-- 逻辑配置：一个 Owner 下可有多份云端 OpenAI-Compatible 模型配置
CREATE TABLE model_config
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id            BIGINT       NOT NULL,
    display_name        VARCHAR(200) NOT NULL,
    provider_name       VARCHAR(100) NOT NULL,
    enabled             TINYINT      NOT NULL DEFAULT 1,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    current_revision_id BIGINT       NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_mc_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id)

    -- 注意：current_revision_id 不建外键，避免与 model_config_revision.model_config_id 形成循环引用。
    -- 应用层保证 current_revision_id 指向同一 Owner 下本配置的 Revision。
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_mc_owner_id ON model_config (owner_id);

-- 不可变 Revision：具体参数与加密 Secret 快照；创建后不 UPDATE
CREATE TABLE model_config_revision
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    model_config_id          BIGINT       NOT NULL,
    owner_id                 BIGINT       NOT NULL,
    revision_no              INT          NOT NULL,
    provider_type            VARCHAR(50)  NOT NULL,
    display_name             VARCHAR(200) NOT NULL,
    provider_name            VARCHAR(100) NOT NULL,
    base_url                 VARCHAR(500) NOT NULL,
    model_name               VARCHAR(200) NOT NULL,
    temperature              DOUBLE       NULL,
    max_output_tokens        INT          NULL,
    encrypted_api_key        VARCHAR(500) NOT NULL,
    api_key_encryption_version INT         NOT NULL,
    api_key_masked           VARCHAR(50)  NOT NULL,
    created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_mcr_config
        FOREIGN KEY (model_config_id) REFERENCES model_config (id),
    CONSTRAINT fk_mcr_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    -- 每个配置内部 revision 递增且唯一
    CONSTRAINT uk_mcr_config_revision
        UNIQUE (model_config_id, revision_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_mcr_config_id ON model_config_revision (model_config_id);
CREATE INDEX idx_mcr_owner_id ON model_config_revision (owner_id);

-- Owner AI Settings：默认 Chat Model 与 Utility Model
CREATE TABLE owner_ai_settings
(
    id                            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id                      BIGINT      NOT NULL,
    default_chat_model_config_id  BIGINT      NULL,
    utility_model_config_id       BIGINT      NULL,
    updated_at                    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_oas_owner
        FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT uk_oas_owner
        UNIQUE (owner_id),
    CONSTRAINT fk_oas_default_config
        FOREIGN KEY (default_chat_model_config_id) REFERENCES model_config (id),
    CONSTRAINT fk_oas_utility_config
        FOREIGN KEY (utility_model_config_id) REFERENCES model_config (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- System Owner（id=1）预初始化 settings 行，后续按 Owner upsert
INSERT INTO owner_ai_settings (owner_id)
VALUES (1);
