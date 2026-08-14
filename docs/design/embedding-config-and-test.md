# 系统设置：向量模型配置与连接/向量化测试（待 Owner 批准）

> 状态：**草稿，待项目 Owner 批准**。
> 本稿修改 `DECISIONS.md` §12 的跨 Phase 稳定决策（Embedding 由"不可配置的系统级固定配置"改为
> "系统设置中可配置、可测试的单一当前配置"）。批准后更新 `DECISIONS.md` 并新增 ADR 0008，再进入实施。

## 1. 背景与现状

当前 Embedding（向量模型）是纯系统级固定配置：

- `EmbeddingProperties`（`knowflow.embedding`）：baseUrl / apiKey / model（默认 text-embedding-v4）/
  dimensions（默认 1024）/ 超时，来自 `application.yml` 环境变量 `KNOWFLOW_EMBEDDING_*`；
- `IndexInfrastructureConfig` 用 `new EmbeddingClient(properties, baseUrlValidator)` 装配；
- `EmbeddingClient`（薄 HTTP 客户端，非 Spring AI）每次调用都读 `properties`，维度校验在
  `parseEmbeddings` 比对 `properties.getDimensions()`；
- 维度固定 1024，`KnowledgeIndexingService` 传给 `qdrantClient.ensureCollection`，不匹配抛
  `INDEX_SCHEMA_FAILURE`；
- **无任何 Embedding 配置实体、API 或前端入口**。

现状问题：改向量模型要改环境变量并重启；没有页面可查看/校验当前配置是否可用。

## 2. 目标

- 在系统设置（`/model-settings`）增加"Embedding 向量模型"配置区，可配置 baseUrl / apiKey / modelName。
- 提供"测试连接与向量化"能力：真实调用 embedding API 返回探针向量，自动探测 dimension。
- **保存前必须通过一次真实测试**（服务端强制），并校验维度与已索引维度一致（不一致则阻止保存，提示需重建）。
- 运行时 Embedding 配置事实源从 `application.yml` 迁移到 MySQL（yml 作首次引导 seed）。

## 3. 已确认决策

| # | 决策 |
|---|---|
| 1 | **单一当前配置**：系统设置中一份 Embedding 配置，保存即生效；不引入多 profile |
| 2 | **独立数据形态**：独立于 Chat `ModelConfig`，新建 `embedding_config` 表，无 revision 链 |
| 3 | **保存前必测**：`PUT` 保存由服务端重跑真实测试；设置页另有手动"测试"按钮可随时预检 |
| 4 | **重建另开**：全量重建流程不在本次范围；维度变化时阻止保存（Q6 决策） |
| 5 | **维度自动探测**：测试返回向量后自动识别维度并存储展示，不手填 |
| 6 | **维度变化阻止保存**：新配置维度 ≠ 当前 Qdrant 已索引维度 → 拒绝保存，保持旧配置生效 |
| 7 | **安全沿用 §7**：API Key 主密钥加密 + 掩码回显；Base URL 过 `BaseUrlValidator`（HTTPS、禁 localhost/私网/内嵌凭据/重定向） |
| 8 | **DB 事实源 + yml 引导**：启动时无 `embedding_config` 行则用 `application.yml` 初始化一行，此后以 DB 为准 |
| 9 | **前端同页**：在 `ModelSettingsView` 加 Embedding 区块，不新增路由 |

## 4. 数据模型

### 4.1 新表 `embedding_config`（单行单例）

```sql
CREATE TABLE embedding_config
(
    id                         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id                   BIGINT       NOT NULL,
    base_url                   VARCHAR(500) NOT NULL,
    model_name                 VARCHAR(200) NOT NULL,
    encrypted_api_key          VARCHAR(1000) NOT NULL,
    api_key_encryption_version INT          NOT NULL,
    api_key_masked             VARCHAR(100) NOT NULL,
    dimension                  INT          NOT NULL,
    row_version                INT          NOT NULL DEFAULT 0,
    created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_emb_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

- **单行单例**：固定 `id = 1`（System Owner 单例），应用内 upsert，不提供删除。
- 字段对齐 `model_config_revision`：加密 Key 存储用 `encrypted_api_key` +
  `api_key_encryption_version` + `api_key_masked`（复用 `SecretEncryptionService`，主密钥
  `KNOWFLOW_SECURITY_MASTER_KEY`）。
- `dimension`：测试通过后自动探测写入；不手填。
- 超时（connectTimeoutMillis / readTimeoutMillis）**不进表**，保持 `EmbeddingProperties` 系统级配置。
- migration：`V11__create_embedding_config.sql`（建表）；yml 引导 seed 由应用启动逻辑完成（migration 读不到 yml）。

## 5. API 设计

基础路径 `/api/v1`，BIGINT 序列化为字符串（既有规范）。新增模块 `embeddingconfig`。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/embedding-config` | 当前配置（API Key 只回显掩码；无行时返回 yml 引导默认 + `configured=false`） |
| `PUT` | `/api/v1/embedding-config` | 保存：**服务端重跑真实向量化测试**，通过后持久化自动探测的 dimension；维度 ≠ 已索引维度则拒绝 |
| `POST` | `/api/v1/embedding-config/test` | 手动预检：用候选 baseUrl/apiKey/modelName 调真实 embedding，返回 `{ok, dimension}` 或 Problem Details |

### 5.1 `PUT` 保存语义（保存前必测）

- 请求体：`{ baseUrl, apiKey?, modelName }`。**首次保存（尚无配置行）时 `apiKey` 必填**，
  缺失返回 400 `INVALID_ARGUMENT`（"首次保存向量模型配置必须提供 API Key"）；编辑时
  `apiKey` 为空表示沿用现有加密 Key（`target` 复用已加载行，原加密 Key 字段不变，
  `updateById` 原样持久化）。存量空 Key 行（旧 bootstrap seed 出的空串密文）解出空串时
  返回 400 提示重新输入，避免空 key 探针调上游。
- 服务端流程：校验 baseUrl（`BaseUrlValidator`）→ 用候选配置调一次真实 embedding（固定探针文本
  `"KnowFlow 向量化测试"`）→ 自动探测 dimension → 读 Qdrant 集合当前维度：
  - 无集合（尚未索引任何内容）→ 任意维度可保存；
  - 有集合且 `dimension ≠ 集合维度` → 拒绝，Problem Details
    `errorCode=向量模型维度变更需先重建索引`（HTTP 409），旧配置保持生效；
  - 一致 → 加密 Key（首次必填或编辑沿用）、写行、返回配置（含掩码与 dimension）。
- 由此"必须通过测试"由服务端强制，不依赖前端先测再传维度（防篡改）。
- 启动引导（`EmbeddingConfigBootstrap`）：仅当 `application.yml` 同时配置了 `base-url` 与
  `api-key` 且 DB 无行时 seed 一次；缺任一则以 `configured=false` 引导用户在系统设置补全。

### 5.2 `POST /test` 手动预检

- 请求体同上（apiKey 必填，因未保存）。
- 返回 `{ ok: true, dimension, modelName }`；失败返回 Problem Details，复用
  `ErrorCode.EMBEDDING_AUTH_FAILURE` / `EMBEDDING_UNAVAILABLE`（401/403 与网络/5xx/429 分类与
  `EmbeddingClient` 一致）。

## 6. 运行时接线改造

- 引入 `EmbeddingConfig` 值对象（baseUrl / apiKey / model / dimension）与
  `EmbeddingConfigService`（读 DB 行；无行回退 yml 引导）。
- `EmbeddingClient` 重构：核心 `embed` 逻辑改为接受一个 `EmbeddingConfig` 快照参数，不再持
  `EmbeddingProperties` 读全量字段；运行时 Bean 由 `EmbeddingConfigService` 提供当前配置，
  `test` 路径由候选配置构造快照。超时仍取 `EmbeddingProperties`。
- 维度校验（`parseEmbeddings` 比对）改用配置快照的 `dimension`。
- `KnowledgeIndexingService` 传 Qdrant 的维度改读当前配置 `dimension`（不再读
  `EmbeddingProperties.dimensions`）。
- 启动 Runner：`embedding_config` 无行时，用 `EmbeddingProperties`（yml/env）seed 一行（若 yml
  未配置 baseUrl 则跳过，保持未配置态）。

## 7. 前端变更（`ModelSettingsView.vue`）

- 新增"Embedding 向量模型"区块：
  - 字段：baseUrl、apiKey（掩码展示，编辑留空表示不改）、modelName；
  - dimension 只读展示（取已保存配置或最近一次测试结果）；
  - 按钮：**"测试连接与向量化"**（POST /test，成功显示 dimension、失败显示错误）、**"保存"**
    （PUT，服务端重测；维度变更被拒时展示提示"需先重建索引"）；
  - 未配置态：显示 yml 引导默认值 + `configured=false` 提示，引导完成首次配置。
- 交互复用现有 `runTest` 模式与 Element Plus 表单校验。

## 8. 明确不在本次范围（记为债务）

- **全量重建流程**：切换维度后重建已有 Qdrant 索引（本次以"维度变化阻止保存"兜底）。
- 多 Embedding profile / 在线切换。
- Rerank 模型、向量数据库（Qdrant）配置——Qdrant 已有 `QdrantProperties` 独立配置。
- Embedding Profile 显式版本化升级（§12 既有概念保留，不在本次落地完整机制）。

## 9. 跨 Phase 决策变更清单（须 Owner 批准）

修改 `DECISIONS.md`：

1. **§12** "Embedding 是单一系统级云端配置，不进入用户 ModelConfig 页面" →
   "Embedding 是系统级配置，在系统设置中配置与测试（单一当前配置，独立于 Chat ModelConfig，见 ADR 0008）"。
2. **§12** "V1 不在线切换 EmbeddingModel；更换需要未来全量重建流程" →
   "支持在系统设置中修改 Embedding 配置；维度变化需先全量重建，重建完成前保持旧配置生效；重建流程在 V1 后跟进（见 ADR 0008）"。

新增 **ADR 0008**：记录"Embedding 配置进系统设置 + 保存前必测 + 维度变化阻止保存"的动机、影响与替代方案。

## 10. 验证方案

- 后端单测/集成：`EmbeddingConfigService` 读写与 yml 引导 seed；`test` 端点（用本地
  OpenAI-Compatible Embedding Stub，§17 既有 Stub 支持 Embedding）成功/401/网络错误分类；
  `PUT` 保存重测强制、维度变化拒绝（409）、加密 Key 掩码回显。
- 检索回归：`KnowledgeIndexingService` 维度来源切换后正常 ensureCollection。
- 真实云端 smoke 显式运行（Live Provider Smoke，不进入默认单测）。
- 前端：设置区块渲染、测试成功/失败展示、保存流。
- 执行 `scripts/verify-fast.sh`、`scripts/verify-integration.sh`。
