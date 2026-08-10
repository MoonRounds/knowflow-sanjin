# Phase 3 REVIEW：Conversation 与 SSE

## Review 目标

验证流式聊天的持久化、并发、取消、重新生成和模型追溯是否正确，尤其检查长事务和资源泄漏。

## 重点检查

- User Message 与 Assistant Attempt 创建是否可重复或丢失。
- SSE 生命周期中是否错误地持有 MySQL 事务/连接。
- activeGeneration 是否在所有 success/failure/cancel/disconnect 路径清理。
- 同 Conversation 并发是否只靠进程内锁。
- regenerate 失败是否错误覆盖原 active answer。
- failed/cancelled/partial content 是否被当成正常历史。
- ModelConfig 是否在开始生成时锁定 Revision。
- API Key 或 Provider 原始错误是否进入 Message/SSE。
- clientMessageId 是否有数据库唯一约束和并发处理。
- 前端 AbortController、流解析和最终状态对账是否可靠。
- 是否提前实现 Redis、RAG 或 Extraction。

## 必跑验证

- Stub SSE 正常、慢响应、首 delta 前失败、中途失败、取消和断开场景。
- 并发双请求同一 Conversation。
- 重复 clientMessageId。
- 新重生成失败和成功切换。
- 删除 active Conversation 返回 409。
- 前后端完整手动聊天与模型切换。

## 阻塞重点

active generation 永久卡死、重复扣费、Message 顺序错乱、长事务、Secret 泄漏或失败回答进入 active history 均至少为 P1。

