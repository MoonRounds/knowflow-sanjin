# 知识库管理重构设计（待 Owner 批准）

> 状态：**草稿，待项目 Owner 批准**。
> 本稿改变 `DECISIONS.md` §10/§12 的跨 Phase 稳定决策（多对多 → 单归属），
> 按 `DECISIONS.md` §19 与 `CLAUDE.md` §2，批准后须更新 `DECISIONS.md` 并新增 ADR 0007，
> 再进入实施。本文只做设计，不包含代码改动。

## 1. 背景与问题

对照 `interview-guide`（智能 AI 面试官）的知识库管理：它的分类只有一维（`knowledge_base.category`），
知识单元统一是"上传文件"，走"上传 → 切块 → 向量化 → 检索"单一线管，因此不显乱。

当前 KnowFlow 知识库的乱点（已通过代码与 migration 调研确认）：

| # | 乱点 | 证据 |
|---|---|---|
| 1 | **Item↔KB 多对多**，一条知识可挂多库，与"主题分类"职责重叠 | `knowledge_base_item` 关联表（V6），`knowledge_item` 无 `kb_id` |
| 2 | **软删方式三种并存** | KB/关联表用 `deleted` 布尔；Item 用 `status=DELETED`；Tag 干脆硬删无字段 |
| 3 | **无分类字段**，只有 KnowledgeBase（逻辑域）+ Tag（无管理 UI） | `grep category/folder` 无结果；Tag 无 Controller |
| 4 | **创建链路不强制归属**，可能产生"无归属"堆积 | Item 可零关联 KB |
| 5 | 前端 `KnowledgeBasesView` 一页塞下 KB 管理 + 建笔记 + 上传 + 列表 | `frontend/src/views/KnowledgeBasesView.vue` |

interview-guide 的启示不是照搬它的表结构，而是两个原则：
**分类维度单一、每条知识有且只有一个主要归属**。

## 2. 目标概念模型

```text
KnowledgeBase 1--N KnowledgeItem N--N Tag
                        |-- 1--N KnowledgeChunk
                        `-- 0..1 FileMetadata
```

- **KnowledgeBase = 主题分类**：一库一主题（如"Java面试"），`display_name/normalized_name/description` 即分类元数据。
- **KnowledgeItem 单归属**：`kb_id` 必填，一条知识只能属于一个库；跨主题需求交给 Tag。
- **Tag = 跨库轻量标签**：owner 级、多对多、保留，补齐管理 UI 与列表过滤；Tag 不参与 RAG 检索（见 §7）。
- 三条来源（AI 提取 / 手写笔记 / 上传文件）保持统一 `KnowledgeItem` 模型，`source_type` 区分。

## 3. 数据模型变更

### 3.1 `knowledge_item`：单归属 + 软删统一

- 新增 `kb_id BIGINT NOT NULL`（外键 → `knowledge_base.id`），并加索引 `(owner_id, kb_id, deleted)`。
- 删除 `status` 列（当前仅表达 `ACTIVE/DELETED` 两态，语义并入 `deleted`；ADR 0003 的"两态软删 + 异步清理"机制不变）。
- 新增 `deleted TINYINT NOT NULL DEFAULT 0`。
- 新增唯一约束思路：同一 owner 下"KB 内标题归一化后唯一"暂不做（当前无此约束，保持现状），仅保证归属与删除语义统一。

### 3.2 `knowledge_base_item`：删除

多对多关系取消，表整体移除。随之：

- `KnowledgeBaseService.ensureNoOrphanedItems` 的 JdbcTemplate 反向依赖**自然消解**（不再需要直查 knowledge 模块的表）。
- 删除 KB 的零归属保护改为：**KB 下仍有 `deleted=0` 的 Item 时阻止删除**（原"删除导致 Item 零归属则阻止"的方向反转，见 §5 决策变更）。

### 3.3 `tag`：补软删

- 新增 `deleted TINYINT NOT NULL DEFAULT 0`（原为硬删，硬删会使 `knowledge_item_tag` 悬空）。
- 唯一约束由 `(owner_id, normalized_name)` 改为 `(owner_id, normalized_name, deleted)`，与 KB 一致，允许软删后同名重建。
- `knowledge_item_tag` 保持现有 `deleted` 布尔。

### 3.4 软删统一约定

全表统一为 `deleted TINYINT NOT NULL DEFAULT 0`（1 = 已删）：

| 表 | 现状 | 目标 |
|---|---|---|
| `knowledge_base` | `deleted` 布尔 | 保留 |
| `knowledge_item` | `status` 枚举 | 改 `deleted` 布尔（`status` 删除） |
| `tag` | 无软删（硬删） | 补 `deleted` 布尔 |
| `knowledge_base_item` | `deleted` 布尔 | 表删除 |
| `knowledge_item_tag` | `deleted` 布尔 | 保留 |

## 4. API 变更清单

基础路径 `/api/v1`，BIGINT 序列化为字符串（既有规范）。

**新增 Tag 管理**（独立模块或并入 knowledge 模块，实施时定）：

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/tags` | 列表，含每个 Tag 的条目计数 |
| `POST` | `/api/v1/tags` | 创建（规范化去重） |
| `PATCH` | `/api/v1/tags/{id}` | 重命名 |
| `DELETE` | `/api/v1/tags/{id}` | 软删；`knowledge_item_tag` 关系行软删 |
| `POST` | `/api/v1/tags/merge` | 合并到目标 Tag（可选，纳入本次） |

**修改既有接口**：

| 接口 | 变更 |
|---|---|
| 建笔记 `POST /api/v1/knowledge-items` | body 新增必填 `knowledgeBaseId` |
| 列表 `GET /api/v1/knowledge-items` | 新增可选过滤 `knowledgeBaseId`、`tagId` |
| 上传 `POST /api/v1/files` | `knowledgeBaseIds`（数组）→ `knowledgeBaseId`（单值必填） |
| 按库列出 `GET /api/v1/knowledge-bases/{id}/items` | 新增（KB 详情页条目列表） |

**不变**：KB CRUD、Item 详情/编辑/删除、候选审核、上传下载。

## 5. 跨 Phase 决策变更清单（须 Owner 批准）

修改 `DECISIONS.md`：

1. **§10** "KnowledgeItem 必须关联 1～N 个 KnowledgeBase" → "KnowledgeItem 必须归属于且仅归属于一个 KnowledgeBase（`kb_id` 必填）"。
2. **§10** "KnowledgeItem 与 KnowledgeBase 多对多" → "KnowledgeItem 与 KnowledgeBase 一对多（单归属）；跨主题语义由 Tag 表达"。
3. **§10** "删除 KnowledgeBase 若导致 Item 零归属则阻止" → "删除 KnowledgeBase 时，其下仍有活动 Item 则阻止（方向反转）"。
4. **§12** Point metadata 中 `knowledgeBaseIds` → `knowledgeBaseId`（单值）。
5. **domain-model-and-state-machines.md** 核心关系图 `KnowledgeBase N--N KnowledgeItem N--N Tag` → `KnowledgeBase 1--N KnowledgeItem N--N Tag`。

新增 **ADR 0007**：记录本次从多对多到单归属的动机（对照 interview-guide 单分类、消除职责重叠）、影响面（Qdrant payload、上传 API、删除保护、Tag 补软删）与替代方案（保留多对多 + Tag 重新定位，因职责重叠被否）。

## 6. 检索链路变更

- **Qdrant payload**：`knowledgeBaseIds` 数组 → `knowledgeBaseId` 单值。索引任务写 payload 时同步改。
- **RetrievalService** 过滤：`key=knowledge_base_ids, match any` → `key=knowledgeBaseId, match`，多个选中库用 `should` OR。
- **二次校验**：`loadActiveKbIds`（读 `knowledge_base_item`）→ 读 `knowledge_item.kb_id` + `deleted` + `knowledge_base.enabled/deleted`。
- **Router 不变**：仍输出 0~3 个 KnowledgeBase，从 enabled 且有可检索 Item 的库中选。单归属后每个 chunk 属于唯一库，Router 多库检索语义仍成立。

## 7. Tag 检索边界

本次只做 Tag 管理 UI + 知识列表按 Tag 过滤；**Tag 不参与 RAG 检索**（`RetrievalService`、Qdrant payload、Router 输出均不改入 Tag）。理由：症状是概念模型 + 分类体系，不是检索；单归属后 KB 已是主分类，检索按 KB 过滤够用。Tag 检索列为后续项。

## 8. 前端变更清单

- `KnowledgeBasesView.vue` 拆分：
  - 知识库列表页：KB 卡片，显示主题名、条目数、启用状态、管理入口。
  - 知识条目列表页：按 KB 过滤（必选其一），可叠加 Tag 过滤；显示 sourceType、索引状态。
  - 详情页复用现有 `KnowledgeItemDetailView.vue`。
- 新增 **Tag 管理**：独立页面或弹层，增删改、重命名、合并。
- 上传/建笔记入口从 KB 内进入，目标 KB 预选，后端必填 `knowledgeBaseId`。
- 路由调整：新增 Tag 管理页路由；`/knowledge-bases` 语义明确为"知识库列表"。

## 9. 迁移策略

V1 无认证、开发期，预期无真实生产数据。迁移以 schema 变更为主，`V11__knowledge_single_ownership.sql` 草案：

```sql
-- 1. 软删统一：tag 补 deleted
ALTER TABLE tag ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;
DROP INDEX uk_tag_owner_name ON tag;
ALTER TABLE tag ADD CONSTRAINT uk_tag_owner_name UNIQUE (owner_id, normalized_name, deleted);

-- 2. item 单归属 + 软删统一
ALTER TABLE knowledge_item ADD COLUMN kb_id BIGINT NULL;
-- 回填：每 item 取最早创建的关联作为主归属（V1 预期无数据，脚本兜底）
UPDATE knowledge_item ki
  JOIN (SELECT knowledge_item_id, MIN(created_at) created_at
          FROM knowledge_base_item
         WHERE deleted = 0
         GROUP BY knowledge_item_id) t ON t.knowledge_item_id = ki.id
  SET ki.kb_id = (SELECT knowledge_base_id FROM knowledge_base_item
                   WHERE knowledge_item_id = ki.id AND deleted = 0
                   ORDER BY created_at LIMIT 1);
ALTER TABLE knowledge_item MODIFY COLUMN kb_id BIGINT NOT NULL;
ALTER TABLE knowledge_item ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;
UPDATE knowledge_item SET deleted = 1 WHERE status = 'DELETED';
ALTER TABLE knowledge_item DROP COLUMN status;
ALTER TABLE knowledge_item ADD CONSTRAINT fk_kitem_kb FOREIGN KEY (kb_id) REFERENCES knowledge_base (id);
CREATE INDEX idx_kitem_kb ON knowledge_item (owner_id, kb_id, deleted);

-- 3. 删除多对多关联表
DROP TABLE knowledge_base_item;
```

> 若实施前发现已有真实存量数据需保留"多归属"历史，须先与 Owner 确认取舍（保留主归属、其余落 Tag 或丢弃），再调整回填脚本。

## 10. 明确不在本次范围（记为债务）

- `knowledge_candidate` 的 `ai_*/draft_*` 双字段快照冗余：独立工程债，不混入本次。
- Tag 参与 RAG 检索：后续项（§7）。
- 独立语义搜索页：`DECISIONS.md` §13 明确 V1 不做。
- 多级文件夹树：interview-guide 自身未做，引入"位置唯一性"问题，不纳入。

## 11. 验证方案

- 后端单元/集成：单归属约束（`kb_id` 必填）、软删统一语义、上传单库校验、Tag CRUD/合并/软删、列表按 KB/Tag 过滤。
- 检索：payload 单值；二次校验改走 `kb_id`；Router 多库检索回归。
- 执行 `scripts/verify-fast.sh`、`scripts/verify-integration.sh`。
- E2E 回归 V1 双闭环（个人笔记闭环：建 KB → 建笔记/上传 → 索引 → 检索引用）。
