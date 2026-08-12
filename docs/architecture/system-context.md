# V1 系统上下文与核心调用链

KnowFlow V1 是本地优先的模块化单体：Vue SPA 与 Spring Boot 后端独立构建，MySQL 是业务事实源；
Redis、RabbitMQ、Qdrant 和本地文件目录分别承担可重建投影、异步投递、向量索引和上传原文件存储。

## 运行时视图

```text
Browser / Vue 3
    | HTTP + POST response stream (SSE)
    v
Spring Boot /api/v1
    |-- MySQL 8.4       业务、消息、任务、Chunk、Trace 事实源
    |-- Redis 7         最近 active Turns 的可丢失 Memory 投影
    |-- RabbitMQ 3.13   Extraction / Document Parse / Index taskId 投递
    |-- Qdrant 1.12     可重建 dense vector index
    |-- Local Volume    Markdown/TXT 原文件
    `-- OpenAI-Compatible APIs
         |-- ChatModel / Utility Model（ModelConfig Revision）
         `-- Embedding Model（系统级固定配置）
```

后端是单进程模块化单体，不拆微服务；RabbitMQ 只承载已确认的 ProcessingTask 异步边界，
不进入实时 Chat 主链路。

## 核心调用链

### Chat / SSE / RAG

```text
ChatView
-> GenerationController
-> GenerationService
-> GenerationPrepare (Tx1)
   锁 Conversation；写 User + GENERATING Assistant；认领 active slot；锁定 Model Revision
-> executor 线程（无长事务）
   Redis Memory（故障则由 MySQL 重建）
   -> Utility Router
   -> Qdrant retrieval（失败则 RAG_DEGRADED）
   -> ChatModel stream
   -> SSE started/stage/delta/sources/completed|failed
-> GenerationTraceService (Tx2)
   写 Assistant 终态 + Generation Trace
-> clear active slot；成功后刷新 Redis Memory 投影
```

SSE 客户端断开、用户停止、Provider 失败都必须经 `GenerationFinalizer` 收敛消息终态并释放
active slot。流式正文不逐 token 写 MySQL；成功、失败或取消时一次性保存最终/部分正文。

### 对话知识提取与确认

```text
ChatView 显式触发 extraction
-> ExtractionService (业务 Tx)
   固定 cutoff、Utility Revision、input hash；写 snapshot + PENDING ProcessingTask
-> after-commit 发布 taskId
-> ExtractionTaskConsumer
   claim -> 读取 cutoff 内完整 active Turns -> Utility Structured Output
   -> Tx 内写 0..N Candidate -> SUCCEEDED
-> CandidatesView 编辑草稿
-> CandidateConfirmService (单 Tx、幂等)
   CONFIRMED Candidate + ACTIVE KnowledgeItem + PENDING Index Task
```

### Manual Note / Upload / Index

```text
Manual Note:
Knowledge UI -> KnowledgeService (单 Tx)
ACTIVE Item + KB/Tag 关系 + PENDING Index Task -> after-commit publish

Upload:
Knowledge UI -> DocumentUploadService (单 Tx + 文件补偿)
原文件 + FileMetadata + Item + PENDING Document Task -> after-commit publish
-> DocumentParseTaskConsumer -> 规范 Markdown 正文 + PENDING Index Task

Index:
IndexTaskConsumer claim
-> MySQL Chunk
-> Embedding API
-> Qdrant upsert/delete
-> Item INDEXED + Task SUCCEEDED
```

Embedding/Qdrant 调用不与 MySQL 组成分布式事务。失败由稳定错误码、有限 TTL Retry、最终
`FAILED + DLQ` 和手动 Retry 收敛；MySQL 事实与幂等 Consumer 保证可恢复。

## Owner 与安全边界

- `CurrentOwnerProvider` 固定返回 System Owner `id=1`；Controller 不接收客户端 ownerId。
- MySQL 查询、Redis key、ProcessingTask、Qdrant payload/filter 均携带 owner 边界。
- API BIGINT ID 统一为字符串；错误使用 Problem Details、稳定 `errorCode` 与 `correlationId`。
- V1 没有应用内认证，只能运行在 localhost、可信内网或已有外层认证/网络保护之后。

## 端口与部署

默认宿主端口：Frontend `5173`、App `8080`、MySQL `3306`、Redis `6379`、RabbitMQ
`5672/15672`、Qdrant `6333/6334`。`docker-compose.yml` 中所有发布端口均绑定
`127.0.0.1`；完整容器部署把上传目录挂载到 `knowflow-files` Volume。

相关文档：

- [领域模型与状态机](./domain-model-and-state-machines.md)
- [后端包结构](./backend-package-structure.md)
- [Processing Retry/DLQ 运行手册](../development/processing-retry-dlq-runbook.md)
- [Qdrant 与 Embedding](../development/qdrant-embedding.md)
