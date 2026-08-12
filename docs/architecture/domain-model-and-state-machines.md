# V1 领域模型与状态机

## 核心关系

```text
System Owner (id=1)
|-- ModelConfig 1--N immutable Revision
|-- Conversation 1--N Message
|      User Message 1--N Assistant Generation Attempt
|      Conversation 1--N Extraction Snapshot 1--N Candidate
|                                      Candidate 0..1 -> KnowledgeItem
|-- KnowledgeBase N--N KnowledgeItem N--N Tag
|                         |-- 1--N KnowledgeChunk
|                         `-- 0..1 FileMetadata
`-- ProcessingTask (businessId/businessKey 指向 Extraction/File/Item)
```

MySQL 保存上述事实。Redis Memory 与 Qdrant Point 都是可由 MySQL 重建的投影。

## Conversation 与 Generation

- User Message 是一轮起点；Assistant Message 本身是一次 Generation Attempt，通过
  `replyToMessageId` 关联 User。
- Assistant：`GENERATING -> COMPLETED | FAILED | CANCELLED`。
- 同一 User 可因 regenerate 有多个 Assistant Attempt；只有一个已完成 attempt 为 active。
- 同一 Conversation 同时最多一个 active Generation，由 MySQL 原子 slot 保证。
- 只有完整、active、`COMPLETED` 的 Turns 进入 Memory 和 Extraction。

## Candidate 与 Extraction

- Extraction snapshot 固定 `conversationId + cutoffMessageId + Utility Revision + input hash`，
  后续新消息不会改变旧任务范围。
- Candidate：`PENDING -> CONFIRMED | REJECTED`，`REJECTED -> PENDING`；`CONFIRMED` 终态。
- Candidate 同时保存 AI 原始字段和用户草稿；confirm 幂等，最多创建一个 KnowledgeItem。
- 0 个候选是成功结果，不是失败。

## KnowledgeItem、索引与文件

- Item lifecycle：`ACTIVE -> DELETED`。删除事务立即令检索不可见，并创建异步 Qdrant 清理任务；
  不引入 `DELETING`（见 ADR 0003）。
- Item index status：`PENDING -> PROCESSING -> INDEXED | FAILED`；手动 Retry 可把领域可见状态
  重置到 `PENDING`。
- `contentVersion` 是 MySQL 内容版本，`indexedVersion` 是当前成功索引版本；新版本索引成功前，
  旧版本仍可服务。
- SourceType：`AI_CONVERSATION / MANUAL_NOTE / UPLOAD_FILE`。
- File parse status：`PENDING -> PROCESSING -> SUCCEEDED | FAILED`；上传 Item 正文只读。
- 文件按 `ownerId + detected MIME + SHA-256(raw bytes)` 去重，文件名不参与身份。

## ProcessingTask

```text
PENDING -> PROCESSING -> SUCCEEDED
                     -> PENDING (有限自动 Retry)
                     -> FAILED  (重试耗尽/终态错误，进入 DLQ)

FAILED -> 新 PENDING Task (manual Retry，retryOfTaskId 关联原任务)
```

任务类型为 `KNOWLEDGE_INDEX / EXTRACTION / DOCUMENT_PARSE`。同一 business key 的活动任务由
唯一约束去重；Consumer 使用条件 claim，重复投递不会重复执行业务。具体恢复操作见
[运行手册](../development/processing-retry-dlq-runbook.md)。

## Model 与检索快照

- Conversation 保存默认 ModelConfig；每轮可切换，Assistant 锁定实际不可变 Revision。
- Owner Settings 保存默认 Chat 与 Utility；Utility 必须先通过 Router/Candidate 结构化能力测试。
- Embedding 是系统级固定 profile，不由用户逐轮切换。
- Generation Trace 保存 Router、retrieval rank/score、source/cited 与 profile 版本，不默认保存完整
  Prompt 或知识正文。
