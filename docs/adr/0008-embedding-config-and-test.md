# ADR 0008：Embedding 配置进系统设置 + 保存前必测 + 维度变化阻止保存

- 状态：已接受
- 日期：2026-08-13
- 决策人：项目 Owner
- 关联：DECISIONS §7、§12；设计稿 `docs/design/embedding-config-and-test.md`

## 背景

DECISIONS §12 原决策为「Embedding 是单一系统级云端配置，不进入用户 ModelConfig 页面」「V1 不在线
切换 EmbeddingModel」，实现为 `EmbeddingProperties`（`knowflow.embedding`）读 `application.yml`
环境变量：baseUrl / apiKey / model（text-embedding-v4）/ dimensions（1024）/ 超时，经
`IndexInfrastructureConfig` 装配薄 HTTP 客户端 `EmbeddingClient`，维度固定 1024。

现状问题：改向量模型要改环境变量并重启；无页面查看或校验当前配置可用性；配置错（如 Key 失效、
Base URL 不可达）只能从索引任务失败侧面暴露。Owner 要求：系统设置中增加向量模型配置，并检查
"能否连接、能否向量化"。

## 决策

- **Embedding 配置进系统设置**：单一当前配置（单行 `embedding_config` 表，id=1 单例，无 revision
  链），独立于 Chat `ModelConfig`。配置项 baseUrl / apiKey / modelName；dimension 由测试自动探测，
  不手填。
- **保存前必测**：`PUT /api/v1/embedding-config` 由服务端用候选配置重跑一次真实向量化测试，通过并
  探测 dimension 后才持久化；`POST /api/v1/embedding-config/test` 为手动预检。安全边界沿用 §7
  ChatModel 同套：API Key 主密钥加密 + 掩码回显；Base URL 过 `BaseUrlValidator`（HTTPS、禁
  localhost/私网/内嵌凭据/危险重定向）。
- **维度变化阻止保存**：新配置维度 ≠ Qdrant 当前集合维度时拒绝保存（HTTP 409，稳定 errorCode），
  旧配置保持生效；全量重建流程 V1 后跟进（本次不实现）。
- **DB 为事实源 + yml 引导**：运行时 `EmbeddingClient` 改读 `EmbeddingConfigService`（DB 行）；
  启动时无行则用 `EmbeddingProperties` seed，保存后以 DB 为准。超时仍属系统级 yml 配置。

## 影响

- Flyway V11 迁移：新建 `embedding_config` 表。
- `EmbeddingClient` 重构：核心 `embed` 逻辑改为接收 `EmbeddingConfig` 快照参数（运行时 DB / 测试
  候选配置），维度校验改用快照；`KnowledgeIndexingService` 传 Qdrant 的维度改读当前配置。
- 新增模块 `embeddingconfig`：Controller / Service / Entity / DTO（对齐 modelconfig 模式）。
- 前端 `ModelSettingsView` 加"Embedding 向量模型"区块：字段 + 只读 dimension + 测试/保存按钮。
- 启动 Runner 负责 yml 引导 seed（migration 读不到 yml）。
- 测试路径调用真实云端：默认单测用本地 OpenAI-Compatible Embedding Stub，Live Provider Smoke
  显式运行（§17）。

## 替代方案

- **完全移除 yml、只从 DB 读**：全新部署必须先配好 Embedding 才能索引，且破坏现有
  `KNOWFLOW_EMBEDDING_*` docker-compose 路径；采用"DB 事实源 + yml 引导 seed"。
- **允许维度变化保存 + 检索降级**：保存后检索可能因维度不匹配直接失败，体验更差；采用阻止保存，
  把切换建模为"先重建再切换"。
- **复用 `model_config` 加 `type=EMBEDDING`**：Embedding 无 temperature/maxOutputTokens、有
  dimension，且不按消息锁定 revision；独立单行表语义更清晰，参数不混用。
