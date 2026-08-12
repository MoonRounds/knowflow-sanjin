# ADR 0003：KnowledgeItem 删除采用两态（ACTIVE/DELETED）+ 异步清理

- 状态：已接受
- 日期：2026-08-11
- 决策人：项目 Owner
- 关联：ADR 0002、DECISIONS §10

## 背景

DECISIONS §10 原定义 KnowledgeItem lifecycle 为三态 `ACTIVE / DELETING / DELETED`，
意图是"先置 DELETING 阻断检索，异步清理 Qdrant 成功后置 DELETED"。

实现时简化为两态：`KnowledgeService.softDelete` 在事务内直接置 `DELETED`，同时创建
删除任务（`submitDeleteTask`）异步清理 Qdrant Point，并调用跨模块生命周期回调
（`UploadFileLifecycleHandler` 清理原文件与 FileMetadata）。检索路径 `RetrievalService`
对 `item.status != ACTIVE` 一律剔除，Qdrant 命中后回查 MySQL 状态二次校验，
因此"直接置 DELETED + 异步清理"与三态语义在可观测结果上等价。

## 决策

- 确认 V1 采用两态软删：`ACTIVE → DELETED`，删除同时创建异步清理任务。
- `DELETING` 中间态不引入。理由：
  - V1 单用户、软删只由 Owner 手动触发，不存在并发编辑与删除竞争窗口的业务价值。
  - 三态需要额外的状态迁移、失败回滚、前端状态展示，复杂度与收益不成比例。
  - 检索正确性已由"status 校验 + Qdrant 命中后回查 MySQL"双重保证，不依赖 DELETING。
- 更新 DECISIONS §10：Item lifecycle 明确为 `ACTIVE / DELETED` 两态，删除语义为
  "事务内置 DELETED + 异步清理 Qdrant；清理失败仅记日志，检索已不可见"。
- 删除任务在 business key 中冻结删除发生时的 `contentVersion`，只清理
  `content_version <= deleteThroughVersion` 的 Point。软删上传恢复时递增
  `contentVersion`；因此恢复后生成的更高版本 Point 不会被迟到的旧删除消息误删。
- FULL 索引任务同样以 business key 中的版本为准；若该版本已不是 Item 当前
  `contentVersion`，Consumer 幂等跳过，旧消息不能回退当前索引状态。

## 影响

- 删除为逻辑删除，`DELETED` Item 保留在 MySQL（满足 DECISIONS §10"删除不级联删除"）。
- Qdrant 清理为最终一致：清理任务失败会重试，最终 FAILED 进入 DLQ 可手动恢复。
- 修复前遗留的 `:0:DELETE` 任务只在 Item 仍处于删除态时执行；Item 已恢复则跳过。
- 未来如需恢复 DELETING 中间态，可在此基线之上引入，不破坏现有数据。
