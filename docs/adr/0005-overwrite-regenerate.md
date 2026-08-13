# ADR 0005：覆盖式重新生成（regenerate 原位覆盖最新 assistant 消息）

- 状态：已接受
- 日期：2026-08-13
- 决策人：项目 Owner
- 关联：ADR 0004、DECISIONS §8、Phase 03 PLAN、Phase 09 PLAN

## 背景

原决策（DECISIONS §8）与 Phase 03 PLAN 规定「一个 User Message 可有多个 Assistant Attempts，
只有一个 completed attempt 为 active」「重新生成创建新 Assistant Attempt，新失败不覆盖旧回答」。
该模型在 UI 上表现为同一轮出现多条 assistant 气泡，旧 attempt 残留在对话中，无法表达用户
「重新生成 = 原位置替换」的心智。

Phase 09 前端验收时发现该语义需要反转：重新生成应复用最新一条 assistant 消息，在原位置清空旧内容
并由新流写回，失败/取消不保留旧回答。这是对已确认跨 Phase 决策的变更，按 DECISIONS §19 记录本 ADR。

## 决策

- regenerate 锁定 conversation 内 sequence 最新的 assistant 消息（`lockLatestAssistantMessage`）。
- 复用该消息（同 id 同 sequence），清空 `content` 与终态元数据（`errorCode` / `ragStatus` / `usage*`），
  置 `GENERATING` 并写回模型快照，由新流在原位置写回最终结果。
- 失败/取消不再保留旧回答：旧内容在 prepare 阶段已清空，终态标记在同一消息上（`FAILED` / `CANCELLED`）。
- 同 id 复用要求：
  - finalizer 按 assistantMessageId 的幂等去重在重新生成开始时放行（`reset`），保证新流可正常终结。
  - trace 按 assistantMessageId 先删后插；无本次 trace（如失败于 RAG 前）也删除旧 trace，避免旧来源残留。
- prepare 阶段清空终态字段必须用显式 `.set(null)` 写库：MyBatis-Plus 默认 NOT_NULL 更新策略会跳过 null 字段，
  且 `strictUpdateFill` 只在字段为 null 时刷新 `updatedAt`（见 `ConversationService.updateMessage`）。

## 影响

- 数据模型：不再产生多条 Attempt；`isActive` 保持单条 active assistant 消息，`replyToMessageId` 不变。
- Extraction 范围（DECISIONS §11「全部完整 active Turns」）不受影响：覆盖后仍有且仅有一条完整 active Turn。
- 数据风险：regenerate 失败会丢失旧回答，语义是「最新一次结果为准」，前端需明示该行为。

## 决策变更

- DECISIONS §8「多个 Assistant Attempts」更新为「覆盖式重新生成」。
- Phase 03 PLAN 中「追加新 Attempt / 失败保留旧回答」规则被本 ADR 取代。
