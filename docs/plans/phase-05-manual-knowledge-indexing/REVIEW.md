# Phase 05 Review：手工知识与异步向量索引

## 1. Review 目标

确认 Manual Note 能可靠地成为可重建的向量索引，并重点审查 MySQL、RabbitMQ、Embedding API 与 Qdrant 之间的一致性和幂等边界。

本 Review 只评审 Phase 05，不要求实现 Router 或 RAG。

## 2. Review 前置输入

- [全局决策基线](../DECISIONS.md)
- [Phase 05 Plan](./PLAN.md)
- Phase 05 起始提交与结束提交；
- 本地验证结果；
- migration、RabbitMQ 拓扑和 Qdrant collection 说明。

## 3. 必查项

### 3.1 范围与架构

- 是否只完成知识沉淀与索引，没有提前实现 RAG/Router；
- MySQL 是否为 KnowledgeItem、Chunk 和 ProcessingTask 的事实源；
- Qdrant 是否明确为可重建索引；
- 是否未引入分布式事务、延迟插件或复杂调度框架。

### 3.2 数据与版本

- Manual Note 编辑是否增加 contentVersion；
- 当前成功索引版本是否与正在构建版本分离；
- 新版本失败时旧索引是否仍可用；
- 旧任务乱序完成是否可能回退当前版本；
- KnowledgeBase 多对多和 Tag 约束是否正确；
- ownerId 是否贯穿所有查询和写入。

### 3.3 事务与可靠消息

- 是否存在“数据库提交成功但消息永久丢失”的窗口；
- 兼作轻量 Outbox 的 ProcessingTask 是否与业务变更处于同一事务；
- 发布失败和应用重启后是否能恢复；
- Consumer 是否避免长数据库事务包住网络调用；
- ProcessingTask 状态迁移是否可并发保护和恢复。

### 3.4 RabbitMQ 重试

- 是否禁止 `requeue=true` 无限重投；
- 是否为独立 TTL Retry Queue，而非混合大量 per-message TTL；
- 是否只重试明确的临时错误；
- 达到上限时 MySQL 状态是否为 `FAILED`；
- 最终消息是否进入 DLQ；
- 是否错误增加了 `DEAD` 业务状态。

### 3.5 Chunk、Embedding 与 Qdrant

- Embedding 模型、维度和 collection 是否一致并在启动时校验；
- Chunk 策略是否简单、确定且有测试；
- Chunk 正文是否只以 MySQL 为事实源；
- Qdrant payload 是否遗漏 userId/knowledgeBaseIds/contentVersion；
- Qdrant 是否错误保存完整 Chunk 正文；
- Point ID 是否确定性生成；
- 重复消费是否只 Upsert 相同 Point；
- 删除、关系变化和旧版本清理是否存在幽灵向量。

### 3.6 前端与错误可见性

- 用户是否能从 KnowledgeBase 创建 Manual Note；
- Item Detail 是否显示真实索引状态和版本；
- Processing 是否保持轻量；
- FAILED 是否有可定位但不泄露秘密的错误摘要；
- Retry 操作是否幂等且有明确反馈。

## 4. 必跑验证

- `scripts/verify-fast.sh`；
- `scripts/verify-integration.sh`；
- 相同消息重复投递测试；
- Embedding/Qdrant 临时失败重试测试；
- 最大重试与 DLQ 测试；
- 版本乱序完成测试；
- 发布失败恢复测试；
- 删除与关系变化后的 Qdrant 清理测试；
- 受控真实 Embedding/Qdrant smoke test。

## 5. 高风险反例

- 创建 Item 后直接发 MQ，数据库回滚却留下消息；
- 数据库提交后进程崩溃，消息没有恢复路径；
- 每次消费生成随机 Point ID；
- 更新时先删除旧 Point，新索引失败导致知识完全不可用；
- 旧版本任务最后完成并覆盖新版本状态；
- 重复或并发点击 Retry 创建多个同时活动的新 ProcessingTask；
- Qdrant payload 保存完整私人笔记；
- Consumer 使用 `requeue=true` 造成热循环；
- 仅看 MQ 队列推断业务任务状态。

## 6. Review 输出格式

按 P0/P1/P2/P3 输出问题。每个问题包含：证据位置、触发路径、影响、违反的 Plan/Decision、最小修复建议和缺失测试。

最后分别给出：

- Spec 结论；
- Standards 结论；
- 是否允许进入 Phase 06；
- 若不允许，必须修复的阻断项。
