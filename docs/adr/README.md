# Architecture Decision Records

跨 Phase 稳定决策的正式基线是 [DECISIONS.md](../plans/DECISIONS.md)。ADR 记录需要单独解释的
已接受变更；若实现需要推翻这些决定，必须先停止并由 Owner 确认。

| ADR | 状态 | 决策 |
|---|---|---|
| [0001](./0001-maven-parent-reactor.md) | 已接受 | 根 Maven Reactor 与 `knowflow-app` 子模块 |
| [0002](./0002-local-file-storage-for-upload.md) | 已接受 | 上传原文件使用本地对象式存储，未来再迁移 MinIO |
| [0003](./0003-item-delete-two-state.md) | 已接受 | KnowledgeItem 两态软删 + 异步 Qdrant 清理 |
| [0004](./0004-auto-generated-conversation-title-and-create-state.md) | 已接受 | AI 自动生成会话标题 + 「➕ 空白态 / 已开始才新建」创建语义 |
| [0005](./0005-overwrite-regenerate.md) | 已接受 | 覆盖式重新生成：同 id 原位替换最新 assistant 消息，失败不保留旧回答 |
| [0006](./0006-conversation-hard-delete-cascade.md) | 已接受 | Conversation 硬删除级联清理消息/提取产物，已确认沉淀的 KnowledgeItem 保留 |
