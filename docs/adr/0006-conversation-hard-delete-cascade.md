# ADR 0006：Conversation 删除改为硬删除级联，保留已沉淀知识

- 状态：已接受
- 日期：2026-08-13
- 决策人：项目 Owner
- 关联：DECISIONS §8、ADR 0003

## 背景

DECISIONS §8 原决策为「Conversation 软删除，不级联删除已沉淀知识」，实现为
`ConversationService.softDelete` 仅把 `conversation.deleted` 置 1。

单用户系统会频繁创建会话；软删除只隐藏会话行，其消息、GenerationTrace、提取任务与候选
全部永久留在 MySQL，长期累积造成数据杂乱、难以清理。Owner 明确要求：删除会话时物理删除
其关联数据。

## 决策

- Conversation 删除改为**硬删除级联**：单事务内按外键依赖顺序删除
  `generation_trace` → `knowledge_candidate` → `knowledge_extraction_task` →
  `chat_message` → `conversation`。不保留 `deleted` 软删标记。
- 原决策「不级联删除已沉淀知识」**保留**：已确认候选沉淀的 `knowledge_item` 不随会话删除；
  V10 迁移把 `knowledge_item.candidate_id` 外键改为 `ON DELETE SET NULL`，仅解除来源关联。
- V10 迁移同时把 `chat_message` 自引用 `reply_to_message_id` 外键改为
  `ON DELETE CASCADE`，使同一会话消息一次 DELETE 不违反自引用外键。
- 删除守卫（保持 DECISIONS §8「active Generation 存在时禁止删除 Conversation」）：
  - active 生成存在 → 拒绝（沿用 `ActiveGenerationExistsException`）；
  - 存在非终态（PENDING/PROCESSING）提取任务 → 拒绝（新增
    `ConversationExtractionInProgressException`，HTTP 409，
    `errorCode=会话正在提取知识中`），避免消费端与删除并发竞态。
- `processing_task` 行（任务事实源兼 Outbox）**不随会话删除**，作为审计台账保留，
  在「处理任务」页的「全部」标签可见。
- Redis Memory 投影在删除事务提交后清理（Redis 故障不阻塞删除）。

## 影响

- 需要 Flyway V10 迁移；现有 FK 名沿用 V5/V8，DROP/ADD 拆为两条语句规避 MySQL
  「同名外键同语句重建」限制。
- 删除为物理删除，被删会话不可恢复；已确认沉淀的 `knowledge_item` 不受影响。
- 守卫的 `SELECT COUNT` 与删除之间存在 TOCTOU 窗口（未对 `processing_task` 加行锁），
  是「阻塞」决策下的可接受取舍；单用户场景冲突概率极低。
- 改动前遗留的 `deleted=1` 软删行不再可达，V10 不负责清理它们（无后续引用）。
- 若未来需要软删除恢复能力，需在本基线之上引入新的删除态与清理任务。
