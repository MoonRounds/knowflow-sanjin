# Phase 05：手工知识与异步向量索引

## 1. 阶段目标

让用户能够在前端创建一条 Manual Note，并让该知识经过可靠、可观察、可重试的异步处理后进入 Qdrant。

本阶段完成的是“知识沉淀与索引”闭环，不包含自动检索和聊天引用。阶段结束时，MySQL 是知识与任务状态的事实源，Qdrant 是可重建的检索索引。

这是 V1 基础设施最密集的阶段。执行时必须按本文检查点逐个完成和验证，不应一次性生成全部代码。

## 2. 开始条件

- Phase 04 已完成并通过独立 Review；
- KnowledgeBase 已可由前端创建和管理；
- 仓库级快速验证脚本可用；
- 本阶段开始前，执行一次真实 Embedding API 与 Qdrant 的最小技术验证：
  - V1 默认采用 Qwen `text-embedding-v4`；
  - 向量维度固定为 `1024`；
  - 只使用 dense vector；
  - 维度、模型标识和 Qdrant collection 配置必须一致。

如果真实 API 的行为与上述基线不一致，应先记录 ADR 并让用户确认，不得静默改变模型或维度。

## 3. 本阶段范围

### 3.1 领域与持久化

- `KnowledgeItem`；
- KnowledgeItem 与 KnowledgeBase 多对多关系；
- Tag 及 KnowledgeItem 与 Tag 的关系；
- `KnowledgeChunk`；
- `ProcessingTask`；
- Manual Note 创建、查看和编辑；
- `contentVersion` 与当前成功索引版本；
- 知识处理状态与错误摘要；
- KnowledgeBase 删除时的知识归属约束。

### 3.2 异步索引

- 用户确认创建或更新 Manual Note 后提交索引任务；
- MySQL 事务内同时保存业务变更和 ProcessingTask；ProcessingTask 本身承担轻量 Outbox 职责，不另建通用 Outbox 框架；
- 事务提交后向 RabbitMQ 发布；
- RabbitMQ Consumer 执行 Chunk、Embedding、Qdrant Upsert；
- Consumer 幂等；
- 原生 TTL Retry Queue + DLX/DLQ；
- 固定延迟档位默认 `10s / 1m / 5m`，配置化；
- 自动重试耗尽后任务状态为 `FAILED`，消息进入最终 DLQ；
- 用户可以从前端手动重试失败任务；
- 应用启动或定时扫描 ProcessingTask 的投递状态，恢复未成功发布的任务，避免数据库提交成功但消息永久丢失。

### 3.3 Qdrant 索引

- 为 V1 建立一个固定 dense-vector collection；
- Point ID 稳定、可重复计算；
- Payload 至少包含：
  - `userId`；
  - `knowledgeItemId`；
  - `knowledgeBaseIds`；
  - `chunkId`；
  - `chunkIndex`；
  - `contentVersion`；
  - `sourceType`；
  - 必要 tags；
- Qdrant 不保存完整 Chunk 正文；
- Chunk 正文以 MySQL 为事实源；
- 更新知识时构建新版本索引，成功后再切换当前索引版本并清理旧版本；
- 删除或失去全部 KnowledgeBase 归属的知识不得继续被检索。

### 3.4 前端

- 从 KnowledgeBase 页面创建 Manual Note；
- KnowledgeItem Detail 展示标题、摘要、正文、来源、知识库关系、标签、内容版本和索引状态；
- Manual Note 可编辑；
- 上传来源知识在未来应为只读，本阶段不实现上传；
- 轻量 Processing 页面展示 `PROCESSING` 和 `FAILED` 任务；
- 可查看简短错误摘要并手动 Retry；
- 不建设 MQ 管理后台或任务调度平台。

## 4. 明确不做

- Knowledge Router；
- 聊天 RAG；
- Citation；
- KnowledgeCandidate；
- Markdown/TXT 文件上传；
- PDF/Word；
- Hybrid Search、Rerank、Query Rewrite；
- 复杂任务调度器；
- RabbitMQ 延迟消息插件；
- 分布式事务。

## 5. 关键模型与约束

### 5.1 KnowledgeItem

至少表达：

- owner；
- `sourceType = MANUAL_NOTE`；
- title、summary、规范正文；
- contentVersion；
- 当前成功索引版本；
- 生命周期状态；
- 索引状态；
- 创建、更新时间。

正文编辑必须增加 contentVersion。旧索引在新版本索引成功前仍可保留；不能先删除可用索引再尝试构建新索引。

### 5.2 ProcessingTask

业务状态只允许：

- `PENDING`；
- `PROCESSING`；
- `SUCCEEDED`；
- `FAILED`。

至少记录：任务类型、关联业务对象、重试次数、最大重试次数、failureCode、lastError、开始/完成时间和必要的幂等键。RabbitMQ 的 Dead Letter 不是业务状态，不新增 `DEAD`。

### 5.3 幂等与并发

- 同一 KnowledgeItem 同一 contentVersion 同时只能有一个活动索引任务；
- Chunk ID 与 Qdrant Point ID 必须稳定；
- 重复投递只能 Upsert 同一批 Point，不能制造重复向量；
- 旧版本任务晚于新版本完成时，不得把当前索引版本回退；
- 手动 Retry 在原任务 FAILED 后创建一个通过 `retryOfTaskId` 关联的新任务；并发或重复点击不得创建多个活动重试任务；
- 任务状态更新需考虑并发 Consumer 和租约超时恢复。

## 6. 实施检查点

### Checkpoint A：知识关系模型与 Manual Note

- Flyway migration；
- KnowledgeItem、KnowledgeBase relation、Tag、Chunk、ProcessingTask 基础模型；
- Manual Note API；
- 所有查询固定经过 CurrentOwnerProvider；
- 前端可创建和查看尚未索引的 Manual Note；
- 单元与 MySQL 集成测试通过。

完成 A 并 Review 数据模型后，再进入 B。

### Checkpoint B：可靠任务提交与 RabbitMQ 拓扑

- 事务内保存 KnowledgeItem 变更与兼作轻量 Outbox 的 ProcessingTask；
- Publisher 根据 Task delivery 状态发布，并能恢复未成功投递的 Task；
- 工作队列、三个 Retry Queue、DLX 和最终 DLQ；
- 明确可重试与不可重试错误分类；
- 禁止 `requeue=true` 无限即时重投；
- Consumer 基础状态机与幂等入口；
- RabbitMQ 集成测试证明 Retry 和最终失败行为。

完成 B 后确认：关闭 RabbitMQ 或模拟发布失败不会丢失任务。

### Checkpoint C：Chunk、Embedding 与 Qdrant 最小纵切

- 简单、可解释的文本 Chunk 策略；
- Chunk 保存到 MySQL；
- 批量调用 Embedding API；
- Qdrant collection 初始化与维度校验；
- 确定性 Point ID 与 Upsert；
- Payload 不包含完整正文；
- 使用 stub embedding 完成自动化测试，使用真实 API 完成受控 smoke test。

此检查点必须先证明“一条 Manual Note 可索引”，再扩展版本切换和删除。

### Checkpoint D：版本、一致性、删除与恢复

- 正文编辑触发新版本索引；
- 新版本成功后原子切换 MySQL 当前索引版本；
- 安全清理旧版本 Point；
- 旧任务乱序完成不会覆盖新状态；
- KnowledgeBase 关系变化同步更新检索边界；
- 删除与重新索引；
- 卡在 `PROCESSING` 的任务可以被安全恢复；
- 失败任务手动 Retry。

### Checkpoint E：前端闭环与仓库验证

- KnowledgeBase → Manual Note → KnowledgeItem Detail；
- 索引状态轮询或轻量刷新；
- Processing 页面；
- FAILED 错误摘要与 Retry；
- OpenAPI client 更新；
- 快速验证、集成验证和必要文档更新。

## 7. 测试要求

至少覆盖：

- Manual Note 创建、编辑和 owner 隔离；
- KnowledgeItem 与多个 KnowledgeBase、多个 Tag；
- 空 KnowledgeBase 归属的处理；
- 相同版本重复提交和重复消费；
- Embedding 临时失败后重试成功；
- Qdrant 临时失败后重试成功；
- 不可重试错误直接进入最终失败；
- 最大重试耗尽后 MySQL 为 `FAILED` 且消息进入 DLQ；
- 旧版本与新版本任务乱序完成；
- 更新期间旧成功索引仍然可用；
- 删除知识后 Qdrant Point 不再可用；
- RabbitMQ 发布失败后的恢复；
- 应用重启后的 PENDING/PROCESSING 恢复；
- 前端创建、查看、编辑和 Retry 的关键交互。

## 8. 阶段验收

通过前端完成：

1. 创建 KnowledgeBase；
2. 创建一条 Manual Note 并关联一个或多个 KnowledgeBase；
3. 看到索引任务进入 `PENDING/PROCESSING`；
4. 最终看到 KnowledgeItem 索引成功；
5. 在 MySQL 中看到规范正文与 Chunks；
6. 在 Qdrant 中看到稳定 ID 和正确 metadata，且无完整正文；
7. 编辑正文后 contentVersion 增加并成功切换到新索引版本；
8. 注入一次可重试失败，观察有限重试后成功；
9. 注入一次最终失败，看到 `FAILED`、错误摘要、DLQ，并可手动重试。

## 9. 阶段交付物

- Manual Note 完整前后端能力；
- 知识、Chunk、任务相关 migration；
- RabbitMQ 可靠发布、重试和 DLQ；
- Embedding 与 Qdrant 索引实现；
- Processing 轻量页面；
- 集成测试与真实 smoke test 说明；
- 必要 ADR、领域文档与运行手册更新。

## 10. 完成后动作

1. 运行 `scripts/verify-fast.sh`；
2. 运行 `scripts/verify-integration.sh`；
3. 执行受控真实 Embedding/Qdrant smoke test；
4. 提交本阶段代码；
5. 在全新 Codex 任务中仅依据 [REVIEW.md](./REVIEW.md) 做独立 Review；
6. Review 通过后再进入 Phase 06。
