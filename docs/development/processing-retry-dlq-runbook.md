# ProcessingTask Retry、DLQ 与恢复运行手册

本手册覆盖 V1 的三类异步任务：`KNOWLEDGE_INDEX`、`EXTRACTION`、
`DOCUMENT_PARSE`。MySQL `processing_task` 是任务事实源；RabbitMQ 消息只携带 task ID，
不能把队列中的消息视为业务事实。

## 状态机与错误分类

```text
PENDING -> PROCESSING -> SUCCEEDED
                     -> PENDING (可重试失败，retryCount + 1)
                     -> FAILED  (重试耗尽或终态失败)

FAILED -> 新 PENDING Task (用户手动 Retry，retryOfTaskId 指向原任务)
```

- 网络、`429`、上游 `5xx`、Embedding/Qdrant 暂时不可用属于可重试失败。
- 认证失败、输入/结构校验失败、任务或文件事实缺失属于终态失败。
- 默认自动重试档位为 `10s / 1m / 5m`；由
  `knowflow.rabbit.retry-delays` 配置，自动重试累计在原任务。
- 重试耗尽或终态失败时，MySQL 先落 `FAILED`、`failureCode`、`lastError`，随后原消息
  `nack(requeue=false)` 进入对应 DLQ。禁止使用无限 `requeue=true`。
- Task 状态和对应领域投影（KnowledgeItem `indexStatus`、FileMetadata `parseStatus`）在同一
  MySQL 事务中迁移；任一回写失败会整体回滚，避免进程崩溃留下 Task 已失败而领域仍
  `PROCESSING` 的永久分叉。事务提交后 Consumer 才 ack/nack。

## 队列拓扑

默认前缀为 `knowflow`，可通过 `KNOWFLOW_RABBIT_PREFIX` 隔离环境。

| 任务 | Work Queue | Retry Queues | 最终 DLQ |
|---|---|---|---|
| Knowledge Index | `knowflow.index.work` | `knowflow.index.work.retry.0..2` | `knowflow.index.work.dlq` |
| Extraction | `knowflow.extraction.work` | `knowflow.extraction.work.retry.0..2` | `knowflow.extraction.work.dlq` |
| Document Parse | `knowflow.document.work` | `knowflow.document.work.retry.0..2` | `knowflow.document.work.dlq` |

Retry Queue 到期后通过 DLX 回到对应 Work Queue；最终 DLQ 的消息体仍是 task ID。

## 定位失败

1. 打开正式前端 `/processing`，切换到“失败”。记录 task ID、task type、business key、
   `failureCode`、重试次数和错误摘要。
2. 通过 `GET /api/v1/processing-tasks?status=FAILED` 核对 MySQL 事实。
3. 按 task type 核对领域状态：
   - Knowledge Index：KnowledgeItem `indexStatus=FAILED`，错误码与 task 一致；
   - Document Parse：FileMetadata `parseStatus=FAILED`，错误码与 task 一致；
   - Extraction：快照仍保留，候选不会因重复投递重复创建。
4. 在 RabbitMQ 管理页只核对相应 DLQ 的消息数量/任务 ID；不要直接修改 MySQL 状态，
   也不要把 DLQ 消息重新投到错误的 work queue。

日志只应记录 task/item/file ID、稳定错误码和摘要。不要记录 API Key、完整 Prompt、私人正文
或上传文件内容。

## 手动 Retry

先修复外部原因（凭据、Provider、Qdrant、文件存储或网络），再在 Processing 页对 FAILED
任务点击 `Retry`。等价 API 为：

```http
POST /api/v1/processing-tasks/{taskId}/retry
```

该操作在一个 MySQL 事务内：

1. 校验原任务属于当前 owner 且状态为 `FAILED`；
2. 创建新的 `PENDING` task，复用 business key，写入 `retryOfTaskId`；
3. 重置对应领域可见状态；Extraction 还会把 snapshot 的 `processingTaskId` 重绑到新 task；
4. 注册 after-commit 发布，只有数据库事务提交后才把新 task ID 发到正确 work queue。

活动任务唯一约束阻止并发或重复点击产生两个活动 retry。原 FAILED task 和 DLQ 消息保留为审计
历史，不应删除或改写。

## 重启与卡死恢复

`ProcessingRecoveryScheduler` 默认每分钟扫描：

- 从未投递或投递后超过 lease 的 `PENDING`：重新发布；
- 超过 lease 的 `PROCESSING`：条件更新回 `PENDING`，重置领域状态，再发布；
- lease 内的新鲜任务不提前重发。

默认 lease 是 10 分钟，由 `KNOWFLOW_RABBIT_PROCESSING_LEASE_TIMEOUT` 配置。Consumer 的
`claim(PENDING -> PROCESSING)` 是条件更新；重复消息或并发 Consumer 只有一个能执行，其他投递
会被 ack 忽略。因此恢复依赖幂等 Consumer，不依赖 RabbitMQ exactly-once。

## 受控演练

```bash
# 单独演练失败、最大重试、DLQ、恢复与关键浏览器失败路径
sh scripts/verify-failure-drills.sh

# 完整 V1 验证（包含上述集成测试和 Playwright 故障路径）
sh scripts/verify-all.sh
```

受控测试覆盖 Extraction/Document/Embedding/Qdrant 失败、最大重试与 DLQ task ID 一致、
PENDING/PROCESSING 恢复、手动 Retry、SSE 断连/停止/重新生成。默认测试只使用隔离容器和本地
模型 stub，不调用真实云端模型。
