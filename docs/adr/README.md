# Architecture Decision Records

跨 Phase 稳定决策的正式基线是 [DECISIONS.md](../plans/DECISIONS.md)。ADR 记录需要单独解释的
已接受变更；若实现需要推翻这些决定，必须先停止并由 Owner 确认。

| ADR | 状态 | 决策 |
|---|---|---|
| [0001](./0001-maven-parent-reactor.md) | 已接受 | 根 Maven Reactor 与 `knowflow-app` 子模块 |
| [0002](./0002-local-file-storage-for-upload.md) | 已接受 | 上传原文件使用本地对象式存储，未来再迁移 MinIO |
| [0003](./0003-item-delete-two-state.md) | 已接受 | KnowledgeItem 两态软删 + 异步 Qdrant 清理 |
