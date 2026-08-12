# Qdrant Collection 与 Embedding 配置

V1 的 ChatModel 与 EmbeddingModel 完全分离。Embedding 使用系统级固定配置；KnowledgeBase 是
逻辑知识域，不映射为独立 Qdrant Collection。

## 默认 Profile

| 项 | 默认值 |
|---|---|
| Collection | `knowflow_dense_v1` |
| Embedding model | `text-embedding-v4` |
| Dimensions | `1024` |
| Distance | Cosine |
| Chunk target / overlap | `800 / 200` 字符 |

环境变量为 `QDRANT_URL`、`QDRANT_COLLECTION`、`KNOWFLOW_EMBEDDING_BASE_URL`、
`KNOWFLOW_EMBEDDING_API_KEY`、`KNOWFLOW_EMBEDDING_MODEL` 和
`KNOWFLOW_EMBEDDING_DIMENSIONS`。示例值见根 `.env.example`，真实密钥不得提交。

## 隔离与数据职责

- 所有 Owner/KnowledgeBase 共用按 Embedding Profile 划分的 Collection。
- Qdrant payload 至少携带 `ownerId`、`itemId`、`chunkId`、`knowledgeBaseIds`、内容/索引版本、
  `chunkIndex`、`sourceType`、tags 与 profile versions。
- Query 必须同时带 owner 与 Router 选中的 KnowledgeBase filter，命中后回 MySQL 校验 Item ACTIVE。
- Qdrant 不保存完整 Chunk 正文；正文与关系保存在 MySQL `knowledge_chunk`。
- Point ID 由 Owner、Item、contentVersion、chunkIndex 确定性生成，重复 upsert 幂等。

## 版本与恢复

Embedding 维度必须与 Collection 一致；不一致是终态 schema failure。V1 不支持在线切换 Embedding
Profile，也没有全量重建 UI。更换模型、维度或 profile 前必须使用新 Collection，并在未来版本实现
明确的全量重建/切换流程。

Embedding 网络/`429`/`5xx` 与 Qdrant 不可用是可重试故障；认证、维度和 payload 校验失败是终态
故障。MySQL Chunk/Item 是事实源，Qdrant 可经 ProcessingTask 手动 Retry 重建。
