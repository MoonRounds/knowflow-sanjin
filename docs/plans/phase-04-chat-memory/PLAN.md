# Phase 4 PLAN：Redis Chat Memory 与多轮上下文

## 目标

在不改变 MySQL Chat History 事实源的前提下，引入普通 Redis Chat Memory 投影，实现可重建、带 TTL、跨模型共享的多轮上下文。

## 前置条件

- Phase 3 Review 已通过。
- Message active/failed/abandoned 语义稳定。

## In Scope

- 普通 Redis 开发容器和 Testcontainer。
- Spring Data Redis。
- 轻量 Spring AI `ChatMemoryRepository` 适配器。
- 最近 N 个完整 active Turns。
- Redis TTL、删除和 MySQL 重建。
- Redis 故障降级。
- Chat Workspace 多轮行为验收。

## Out of Scope

- Redis Stack、RedisJSON、Query Engine、跨会话搜索。
- 通用业务 Cache、Router Cache、RAG Cache。
- 摘要 Memory、向量 Memory。
- Redis 分布式锁。

## 核心规则

- MySQL Message 永远是事实源。
- Redis Key 以 Conversation ID 隔离并使用明确 prefix。
- 只保存 active completed User/Assistant Turn。
- 默认最近 10 轮、TTL 7 天，均配置化。
- 保存/读取顺序稳定；窗口不得从 Assistant 中间开始。
- Redis miss、过期或清空后，从 MySQL 重建。
- Redis 写失败不能让已成功的 MySQL Message 变成失败回答。
- Conversation 删除后主动清理 Memory。
- Model switch 不改变 Memory Key。

## 执行 Checkpoints

### Checkpoint A：Redis Infrastructure

1. Compose 增加普通 Redis，端口只绑定 localhost。
2. 引入 Spring Data Redis，不引入 Spring AI Redis Stack Starter。
3. Testcontainers Redis。

### Checkpoint B：Memory Repository

1. 实现 Spring AI ChatMemoryRepository 所需最小方法。
2. 明确序列化 Schema、版本、key prefix 和 TTL。
3. 不实现高级查询。

### Checkpoint C：Projection and Rebuild

1. 从 MySQL active completed Turns 构建窗口。
2. Generation 成功/重新生成切换后刷新 Memory。
3. 失败、取消和 abandoned Turn 不进入。
4. Redis miss 自动重建；Redis 故障直接从 MySQL 构造当前请求上下文。

### Checkpoint D：Behavior Acceptance

1. 以固定多轮样例验证代词/上下文，如“ConcurrentHashMap 为什么安全？”→“那 JDK7 呢？”。
2. 中途切换模型仍读同一上下文。
3. 清空 Redis 后继续对话结果保持上下文。

## Required Verification

- 序列化/反序列化、TTL、key 隔离 tests。
- 窗口只含完整 active Turns。
- regenerate 后旧 answer 被移出 Memory。
- Redis miss rebuild integration test。
- Redis unavailable fallback test。
- Conversation delete 清理 test。
- 不同 Conversation/Owner 不串 Memory。
- 固定多轮 Stub E2E。

## 验收标准

- Redis 可完全丢失而不丢 Chat History。
- 同 Conversation 跨模型保持上下文。
- Memory 不包含失败、取消、superseded 或 abandoned 内容。
- Redis 没有承担通用 Cache 或正确性锁。

## Phase Handoff

必须讲清 Chat History 与 Memory 的区别、何时刷新投影、Redis 故障时请求如何继续、TTL 为什么不等于历史保留。

