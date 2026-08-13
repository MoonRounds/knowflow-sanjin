# ADR 0009：Document 统一概念与会话手动知识库范围

- 状态：已接受
- 日期：2026-08-13
- 关联：ADR 0004、ADR 0007、`docs/plans/v1.5-implementation-plan.md`

## 背景

ADR 0007 已确认知识内容采用单一 KnowledgeBase 归属。V1.5 Phase 1 将原
`KnowledgeItem` 全链路统一命名为 `KnowledgeDocument`，形成
`KnowledgeBase → KnowledgeDocument → KnowledgeDocumentChunk` 三层结构。

V1 的问答仅使用自动 Router。用户需要在特定会话中把检索范围限制到自己明确选择的一个或多个
KnowledgeBase，同时仍允许问题无关时不进行检索。

## 决策

1. `Document` 是原 `KnowledgeItem` 的统一正式名称，不增加新的领域层级。
2. Conversation 持久化可选的 0～50 个 KnowledgeBase ID；空集合表示自动 Router，非空表示手动范围。
3. 发送与重新生成在创建 Generation 的数据库事务内冻结当时绑定；之后的修改只影响下一轮。
4. 手动模式仍调用 Utility Router 判断 `needRag`，但目录仅包含绑定集合中当前可路由的库；Router 最多选择 3 个。
5. 手动绑定全部失效或暂不可检索时不回退自动模式，RAG 状态为 `NOT_AVAILABLE`；问题无关时为 `NOT_NEEDED`。
6. Conversation 保存的失效 ID 不自动清理。KnowledgeBase 的禁用或删除不被 Conversation JSON 引用阻止；用户可在会话设置中移除失效项。
7. 检索保持一次全局 Top-K + KnowledgeBase OR filter；绑定顺序不代表优先级。
8. Conversation 绑定不改变 Extraction/Candidate 的范围或归属建议语义。

## 理由

- Conversation 级范围能在多轮问答中保持稳定，又不要求每次发送重复提交。
- Tx1 快照消除发送与设置更新的竞态，使 Trace 能解释一次生成实际使用的范围。
- “手动但不可用”不回退 AUTO，避免在用户明确限定范围时意外检索其他知识库。
- JSON 列适合 V1.5 的低规模读取路径；当前没有按知识库反查会话的需求。

## 后果与限制

- JSON 没有外键，失效 ID 是合法且必须被应用层处理的状态。
- 最多 50 个绑定限制 Prompt 和存储大小；Router 最终仍只选 0～3 个库。
- V1.5 不提供每库均衡配额、手动强制命中、独立语义搜索或 Tag 路由。
