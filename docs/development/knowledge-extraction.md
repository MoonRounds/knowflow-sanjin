# 会话知识提取（Phase 7）运行说明

本文档说明会话知识提取与候选审核链路的配置、状态、失败语义与调用链。

## 1. 配置（`knowflow.extraction.*`）

| key | 默认值 | 说明 |
| --- | --- | --- |
| `input-char-budget` | `20000` | 提取输入字符预算；超长直接拒绝（`EXTRACTION_INPUT_OVER_BUDGET`，422），不调用 LLM、不静默截断 |
| `max-candidates` | `10` | 一次提取返回候选上限 |
| `max-knowledge-bases-per-candidate` | `3` | 单个候选最多推荐 KnowledgeBase 数（与 Router 对齐） |
| `max-tags-per-candidate` | `5` | 单个候选最多推荐 tag 数 |
| `max-retries` | `3` | 提取任务自动重试次数 |

环境变量前缀 `KNOWFLOW_EXTRACTION_*`，见 `knowflow-app/src/main/resources/application.yml`。

## 2. 提取范围与幂等

- 提取范围固定为触发时刻（cutoffMessageId）之前的**全部完整 active Turn**：仅 `COMPLETED && isActive` 的
  Assistant 及其 User 消息；正在生成、失败、取消、未完成的轮次整体排除。
- cutoff 由 `ConversationService.lastMessageId` 在事务内确定；任务快照记录 cutoff 与输入字符数，不复制正文。
- 幂等键 = `owner + conversation + cutoffMessageId + extractionProfile + profileVersion + utilityRevisionId`。
  - 相同键已有活动任务（PENDING/PROCESSING）→ 返回已存在任务；
  - 相同键已有终态任务（同 cutoff）→ 返回旧任务；
  - 新消息使 cutoff 变化 → 产生新任务（新快照）。
- 数据库 `uk_kext_dedup` 唯一约束兜底并发；业务键 `EXTRACTION:{conversationId}:{cutoff}:{revisionId}:{profileVersion}`
  由 `processing_task.uk_ptask_business_active` 兜底活动任务并发。

## 3. 提取 Prompt / Schema 版本

- Structured Output schema：`modules/extraction/dto/ExtractionResult.java`（`BeanOutputConverter`）。
  字段：`candidates[].{title, summary, content, knowledgeBaseIds, tags, reason}`。
- 提取 Prompt 构建在 `ExtractionExecutor.buildPrompt`，含候选数量上限、KB 目录、仅可推荐已存在 KB 的约束。
- 非法输出最多**修复一次**（修复 Prompt 只含 schema 约束，不携带正文/问题原文）；仍非法 → 终态 FAILED。
- schema/Profile 版本：`ExtractionConstants.EXTRACTION_PROFILE_VERSION`（当前 1）。版本变化会改变幂等键，产生新任务身份。

## 4. 状态机

`processing_task`（统一任务状态）：
`PENDING → PROCESSING → SUCCEEDED / FAILED`；重试耗尽或终态错误 → `FAILED`，消息进入 `extraction` 独立 DLQ。

`knowledge_candidate`（审核状态）：
`PENDING → CONFIRMED / REJECTED`；`REJECTED → PENDING`（撤销拒绝，唯一回退）；`CONFIRMED` 为终态。

## 5. 失败与恢复语义

- **可重试故障**（网络/超时/5xx/未知）→ `RetryableExtractionException`，递增 retryCount 进入 TTL 重试队列（10s/1m/5m）。
- **终态故障**（结构化输出修复一次仍非法、Utility 不可用、快照缺失）→ `TerminalExtractionException`，直接 FAILED + DLQ。
- 0 候选是**成功结果**（任务 SUCCEEDED），不是失败。
- RabbitMQ 故障：任务留 MySQL 等待恢复扫描重新投递（按 taskType 派发到对应工作队列）。
- Consumer 幂等：claim 并发只胜一个；重复投递直接 ack 忽略，不制造重复候选。

## 6. 确认与 KnowledgeItem 创建

- `POST /candidates/{id}/confirm`：PENDING → CONFIRMED，事务内创建 KnowledgeItem
  （`sourceType=AI_CONVERSATION`、`candidateId` 关联）、KB 关联、Tag 关联，并提交 FULL 索引任务（复用 Phase 05 链路）。
- **幂等**：`knowledge_item.uk_kitem_candidate` 唯一约束保证每个 Candidate 至多一个 Item；并发双击时
  唯一约束拦截，返回已创建 Item 的候选。
- 确认保存**草稿字段**（用户最终编辑内容）；AI 原值永久保留不可变。
- 拒绝不创建 Item；已确认候选不可再次确认、不可编辑草稿。

## 7. 调用链

```
前端「提取知识」→ POST /conversations/{id}/extraction
  → ExtractionService.trigger (事务内确定 cutoff、字符预算校验、幂等查重)
  → TaskSubmissionService.submit(EXTRACTION, businessKey, "extraction") 提交后发布 taskId
  → ExtractionTaskConsumer (extraction.work 队列)
  → ExtractionExecutor.executeWithLookup → execute (读消息→Prompt→Structured Output→校验→落候选)
  → processing_task SUCCEEDED / FAILED

候选审核：
GET /candidates、GET /candidates/{id}、PUT /candidates/{id}/draft、
POST /candidates/{id}/reject|restore|confirm
  → CandidateService / CandidateConfirmService → knowledge_item + KNOWLEDGE_INDEX 任务
```

## 8. 关键端点

| 端点 | 说明 |
| --- | --- |
| `POST /api/v1/conversations/{conversationId}/extraction` | 触发提取，返回 `ExtractionTaskResponse` |
| `GET /api/v1/candidates?status=&page=&size=` | 候选分页（PENDING 优先） |
| `GET /api/v1/candidates/{id}` | 候选详情（AI 原值 + 草稿） |
| `PUT /api/v1/candidates/{id}/draft` | 编辑草稿（`If-Match` 乐观锁） |
| `POST /api/v1/candidates/{id}/reject` / `restore` | 拒绝 / 撤销拒绝（`If-Match`） |
| `POST /api/v1/candidates/{id}/confirm` | 幂等确认并创建 Item |
| `POST /api/v1/processing-tasks/{id}/retry` | 失败提取任务手动重试（复用 Phase 02） |

## 9. RabbitMQ 拓扑

提取任务使用独立工作队列 `knowflow.extraction.work`（复用 `knowflow.work.exchange` 交换机，
独立 routingKey），独立 TTL 重试队列 `knowflow.extraction.work.retry.{0,1,2}` 与
DLQ `knowflow.extraction.work.dlq`。与索引队列互不干扰。
