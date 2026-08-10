# Phase 3 PLAN：Conversation、Message 与 SSE Streaming

## 目标

完成没有 Redis/RAG 的真实多模型流式聊天主链路，建立 Message/Generation 生命周期、重新生成、取消、并发和历史追溯基础。

## 前置条件

- Phase 2 Review 已通过。
- 至少一个 enabled Chat Model 和一个可用于 CI 的 AI Stub。
- 记录起始 commit。

## In Scope

- Conversation 和 Chat Message Schema/API/UI。
- Conversation default Model 与每次发送的 Model override。
- User Message → Assistant Generation Attempt。
- POST 流式响应和版本化 SSE 事件。
- Stop、失败、重新生成、active attempt 切换。
- 同 Conversation 单 active Generation。
- Message History sequence cursor。
- clientMessageId 幂等。
- 基础 Token Usage、模型快照和错误 Trace。

## Out of Scope

- Redis Chat Memory；本阶段只用 MySQL 最近消息构造最小上下文或先按已完成历史读取，但不建立 Redis 投影。
- Knowledge Router、Qdrant、Citation。
- Extraction、Candidate、RabbitMQ。
- 多模型并行比较。

## 数据与状态规则

- Conversation 保存 owner、title、defaultModelConfigId、activeGenerationMessageId、soft delete、rowVersion。
- Message 保存 conversation、role、sequence、content、replyToMessageId、clientMessageId、generationStatus、isActive、Revision、模型快照、usage、error、UTC 时间。
- User Message 先持久化；Assistant Message 初始 GENERATING。
- 成功后保存完整内容并 COMPLETED。
- 失败/取消保存 partial content 和错误状态，不进入后续有效上下文。
- 一个 User Message 可有多个 Assistant Attempts，但只有一个 active completed answer。
- 新重生成失败时，原 active 成功回答保持 active。
- 同 Conversation 使用 MySQL 条件更新认领 active Generation。

## SSE 协议

- `generation.started`
- `generation.stage`
- `content.delta`
- `generation.completed`
- `generation.failed`

事件携带协议版本和字符串 ID。不透传 Provider 原始事件。

客户端断线不支持 resume；页面通过 REST 查询 Message 最终状态对账。

## 执行 Checkpoints

### Checkpoint A：Schema and History API

1. Migration、外键、sequence、client id 唯一约束。
2. Conversation CRUD、软删除和标题规则。
3. Message cursor API 和 DTO。
4. active Generation 存在时禁止删除 Conversation。

### Checkpoint B：Generation Orchestrator

1. 显式 Application Orchestrator，不把流程藏入 Advisor。
2. 事务内创建 User/Assistant Message 和认领 active Generation。
3. 锁定 ModelConfig Revision。
4. 处理 success/failure/cancel/timeout 和 active slot 清理。
5. 流式已输出正文后禁止自动重试。

### Checkpoint C：Regeneration and Idempotency

1. clientMessageId 重复请求返回原状态。
2. 重新生成创建新 Assistant Attempt。
3. 新成功后事务切换 active；新失败不覆盖旧回答。
4. failed/cancelled turn 可继续新问题并成为 abandoned。

### Checkpoint D：Frontend Chat Workspace

1. Conversation 创建、切换、重命名、软删除。
2. Message History、向上加载旧消息。
3. 模型选择与 Conversation 默认更新。
4. 流式 delta、stop、failed、retry/re-generate 状态。
5. 断线后状态对账。

## Required Verification

- Conversation/Message Migration 与 Repository tests。
- 同 Conversation 并发生成只允许一个。
- clientMessageId 幂等。
- SSE 事件顺序与协议测试。
- Provider 在首 delta 前失败、首 delta 后失败、客户端 stop、连接断开测试。
- regenerate success/failure active 切换测试。
- API 字符串 ID、cursor 和 Problem Details tests。
- 前端流解析、取消、断线对账和模型切换 tests。
- 使用 Stub 完成端到端流式聊天，不调用真实 Provider。

## 验收标准

- 同一 Conversation 可在不同轮切换模型并继续聊天。
- 每条 Assistant 回答可追溯实际 Revision。
- 失败和取消不污染 active 历史。
- SSE 不暴露 Provider 原始事件。
- 没有 Redis、RAG、RabbitMQ 或 Knowledge Extraction。

## Phase Handoff

必须讲清生成开始/结束的数据库事务、流式阶段为何不持有长事务、active slot 如何释放、断线如何对账、重新生成如何选择 active answer。

