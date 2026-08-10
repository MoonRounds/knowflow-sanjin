# Phase 1 REVIEW：Owner 与 KnowledgeBase

## Review 目标

验证第一个全栈持久化切片是否满足 Owner 隔离、数据库完整性、API 契约和前端真实可用性。

## 重点检查

- Migration 是否可从空 MySQL 8.4 执行且未依赖手工 SQL。
- `app_user` 是否没有认证字段，System Owner 是否由受控 Migration 初始化。
- Controller/DTO 是否允许伪造 userId。
- 所有按 ID 查询、更新、删除是否同时校验 Owner。
- normalized name 唯一约束是否能处理并发竞态。
- enabled 与 soft delete 是否混用。
- rowVersion 冲突是否真正返回 409，而不是静默覆盖。
- Entity 是否被直接作为 API DTO 暴露。
- 是否出现通用 BaseService/BaseController 或无意义接口。
- OpenAPI 生成类型与实际响应是否一致。
- 前端是否误显示后续 Phase 功能。

## 必跑验证

- Phase 1 要求的全部脚本和 Testcontainers tests。
- 从空 Volume 启动 MySQL 并运行应用。
- 使用第二 Owner 测试越权访问。
- 并发/重复创建相同规范化名称。
- 前端手动 CRUD 和 409 冲突提示。

## 不应报告为缺陷

- 删除 KnowledgeBase 尚未校验关联 Item，因为 Item 不属于本阶段。
- 没有 ModelConfig、Chat、Redis 或 RAG。

## Review 输出

优先报告数据越权、Migration 不可复现、唯一约束缺失、API/前端契约漂移等问题。无阻塞问题时明确给出 Phase 1 通过结论。

