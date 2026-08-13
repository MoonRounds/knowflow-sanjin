# ADR 0007：KnowledgeItem 单归属重构（多对多 → 单归属，Tag 承载跨主题）

- 状态：已接受
- 日期：2026-08-13
- 决策人：项目 Owner
- 关联：DECISIONS §10、§12、ADR 0003；设计稿 `docs/design/knowledge-base-management-redesign.md`

## 背景

DECISIONS §10 原决策为「KnowledgeItem 必须关联 1～N 个 KnowledgeBase」「KnowledgeItem 与
KnowledgeBase 多对多」，实现为 `knowledge_base_item` 关联表；Qdrant Point metadata 保存
`knowledgeBaseIds` 数组。

对照 `interview-guide`（智能 AI 面试官）的知识库管理：分类维度单一（每库一个 `category`），
知识单元统一为上传文件，管线单一，心智清晰。当前 KnowFlow 知识库因此显得杂乱，乱点经调研确认：

- **多对多与 Tag 职责重叠**：一条知识既可挂多库、又可打多 Tag，两套机制都表达"跨主题"，纠缠不清；
- **软删方式三种并存**：KB/关联表用 `deleted` 布尔，Item 用 `status=DELETED`，Tag 硬删无字段；
- **创建链路不强制归属**：Item 可零关联 KB，产生"无归属"堆积；
- 前端 `KnowledgeBasesView` 一页塞下 KB 管理、建笔记、上传、列表多职责。

Owner 确认（2026-08-13）：采纳「单归属」重构，向 interview-guide 的单分类原则靠拢。

## 决策

- **KnowledgeItem 单归属**：`knowledge_item.kb_id` 必填，一条知识只属于一个 KnowledgeBase；
  删除 `knowledge_base_item` 关联表。
- **KnowledgeBase = 主题分类**：一库一主题，`display_name/normalized_name/description` 即分类元数据。
- **Tag = 跨库轻量标签**：owner 级、与 Item 多对多保留，补齐管理 UI 与列表过滤；跨主题语义统一由
  Tag 表达。Tag 本次不参与 RAG 检索。
- **软删统一为 `deleted` 布尔**（全表一致）：`knowledge_item` 去掉 `status` 的删除语义、
  改用 `deleted`；`tag` 补 `deleted` 字段。ADR 0003 的"两态软删 + 异步 Qdrant 清理"机制不变，
  仅删除表达从 `status` 改为 `deleted`。
- **删除 KB 保护方向反转**：KB 下仍有活动（`deleted=0`）Item 时阻止删除（原为"删除导致 Item
  零归属则阻止"）。
- **Qdrant payload 改单值**：`knowledgeBaseIds` 数组 → `knowledgeBaseId`。
- **各创建链路强制归属**：建笔记、上传、AI 提取候选确认都必须归入一个 KB。
- 三条来源（AI 提取 / 手写笔记 / 上传文件）保持统一 `KnowledgeItem` 模型，`source_type` 区分。

## 影响

- Flyway V11 迁移（草案见设计稿 §9）：`knowledge_item` 加 `kb_id`（回填后置 NOT NULL）、加
  `deleted`、删 `status`；`tag` 加 `deleted` 并调整唯一约束；`DROP TABLE knowledge_base_item`。
- API：上传 `knowledgeBaseIds`（数组）→ `knowledgeBaseId`（单值必填）；建笔记必填
  `knowledgeBaseId`；新增 Tag 管理接口与列表按 KB/Tag 过滤。
- 检索：Qdrant filter 改 `key=knowledgeBaseId` 单值 + `should` OR；二次校验从读
  `knowledge_base_item` 改走 `knowledge_item.kb_id` + `knowledge_base` 状态。
- `KnowledgeBaseService.ensureNoOrphanedItems` 的 JdbcTemplate 反向依赖随关联表删除**自然消解**。
- 前端拆分 `KnowledgeBasesView` 为库列表页 + 条目列表页（按 KB/Tag 过滤），新增 Tag 管理。
- V1 无生产数据，迁移成本≈0；若实施前出现真实多归属存量数据，回填策略（主归属/落 Tag/丢弃）
  须再与 Owner 确认。

## 替代方案

- **保留多对多 + 重新定义 Tag**：多主题仍由两套机制表达，职责重叠不消除，被否。
- **多级文件夹树**：引入"一条 Item 放哪个位置"的唯一性问题，interview-guide 自身未做，被否。
- **彻底去掉 Tag、只用 KB 单层分类**：Tag 对跨库检索有真实价值，且存量多对多迁移成本高，被否。
