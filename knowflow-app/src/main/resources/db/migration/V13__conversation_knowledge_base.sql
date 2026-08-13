-- Conversation 可选手动知识库范围；NULL 表示沿用自动 Router。
ALTER TABLE conversation
    ADD COLUMN knowledge_base_ids JSON NULL AFTER default_model_config_id;
