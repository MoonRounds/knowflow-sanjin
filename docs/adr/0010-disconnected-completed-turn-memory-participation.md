# ADR 0010：断连但已完成的生成参与 Memory/Extraction

- 状态：已接受
- 日期：2026-08-14
- 关联：`docs/plans/DECISIONS.md` §8、ADR 0005、commit b14eca6

## 背景

DECISIONS §8 规定「failed/cancelled/abandoned Turn 不进入 Memory 或 Extraction」。

SSE 生成期间，用户切换模块或会话会导致前端 `abort()` SSE 连接。旧行为：后端捕获到客户端断连，
取消 Provider 流并落 `FAILED + 客户端已断开`，该 turn 因 `status=FAILED` 被 Memory/Extraction
排除，生成被打断。

commit b14eca6 引入「断连静默模式」（`SilentSseWriter`）：客户端断连后 Provider 流继续收完，
消息以 `COMPLETED + isActive=true` 落库。由此产生一个语义问题：断连但 Provider 已收完的
turn 是否属于 §8 中的「abandoned」？

Memory 与 Extraction 共用 `ConversationService.loadCompletedTurns`，过滤条件为
`generationStatus = COMPLETED AND isActive = true`；`ChatMessage` 无 `disconnected`/`abandoned`
字段。因此断连后落 COMPLETED 的 turn 会无条件进入两者。

## 决策

1. 「客户端断开但 Provider 流已收完」的 turn 不视为 abandoned；它确实完成（模型返回了完整内容并落库），
   归类为 `COMPLETED`。
2. §8 中的「abandoned」指被放弃的 turn（超时放弃、用户停止且未完成、流式调用本身失败），
   不包括「完成但客户端未持续连接」的情况。
3. 断连后 `COMPLETED` 的 turn 通过标准门 `loadCompletedTurns`（`status=COMPLETED AND isActive=true`）
   进入 Memory 与 Extraction，无需特殊标志或排除逻辑。
4. `ChatMessage` 不新增 `disconnected`/`abandoned` 字段；断连是一个瞬时连接事件，不改变消息终态语义。

## 理由

- 该 turn 的问答内容真实且完整，排除它会丢失上下文连续性，与「发出去就生成完」的产品意图相悖。
- 「abandoned」的自然语义是「未完成且被放弃」；把「完成但客户端没盯」归入 abandoned 是对 §8 的过度解读。
- 不加标志避免投机性抽象（CLAUDE.md §6）；标准 `status` 门已足够表达参与规则。

## 后果与限制

- 行为变化（相对 b14eca6 之前）：断连 turn 从 `FAILED`（不进 Memory）变为 `COMPLETED`（进 Memory/Extraction）。
  已由 Owner 确认接受。
- 一个用户未实际阅读的答案（切走期间生成）会进入 Memory/Extraction。接受：该问答对是真实的，
  对上下文连续与知识提取仍有价值。
- 若未来需要区分「用户是否阅读过」，需额外字段；V1 不做。
- 仍受 §8 约束：已输出正文后不自动重试；断连静默不触发重试，只让当前流跑完。
- 停止请求仍以 `CANCELLED` 优先于静默完成。
