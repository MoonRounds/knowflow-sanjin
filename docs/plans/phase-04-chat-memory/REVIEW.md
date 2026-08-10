# Phase 4 REVIEW：Redis Chat Memory

## Review 目标

确认 Redis 只是可重建投影，不会成为历史事实源或新的单点正确性依赖。

## 重点检查

- 是否误用了 Redis Stack Starter 或高级搜索索引。
- Redis Key 是否包含正确 Conversation 边界和安全 prefix。
- TTL 是否真正设置并在更新时刷新。
- Window 是否按完整 Turn，而不是任意最近消息截断。
- failed/cancelled/superseded/abandoned 是否进入 Memory。
- Redis miss 是否从 MySQL 重建，而不是返回空上下文。
- Redis 故障是否导致已完成 Generation 被回滚/标记失败。
- Conversation 删除与重新生成是否正确刷新/清理。
- 是否偷偷加入通用 Cache 或 Redis Lock。

## 必跑验证

- 清空 Redis 后继续已有 Conversation。
- 停止 Redis 后继续对话，再恢复 Redis。
- 10 轮以上窗口边界。
- 跨模型多轮对话。
- regenerate active answer 替换。
- 两个 Conversation 并行隔离。

## 不应报告为缺陷

- 没有 Router、Knowledge Base 检索或摘要 Memory。

