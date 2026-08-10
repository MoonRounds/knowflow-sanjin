# Phase 06 Review：Knowledge Router、RAG 与诚实引用

## 1. Review 目标

确认自动路由和检索链路在隔离、可追溯和失败降级方面可靠，并确认前端没有夸大“检索来源”与“回答引用”的语义。

## 2. Review 前置输入

- [全局决策基线](../DECISIONS.md)
- [Phase 06 Plan](./PLAN.md)
- Phase 06 起止提交；
- 固定 Eval 数据集与结果；
- 本地快速、集成和可选真实模型验证结果。

## 3. 必查项

### 3.1 Router

- Router 是否只看到当前 owner 下启用、可检索的 KnowledgeBase；
- 是否支持 0～N 且限制最多 3 个；
- 是否错误强制每次都走 RAG；
- Structured Output 是否严格校验；
- 是否只有一次受控修复；
- 失败是否被明确记录为 DEGRADED，而非静默吞掉。

### 3.2 Retrieval 隔离与正确性

- Qdrant 是否同时过滤 userId 与 KnowledgeBase；
- 多知识库是否为 OR 语义和全局 Top-K；
- 是否从 MySQL 加载正文；
- 是否二次验证 owner、当前版本、Item 状态和关系；
- 是否可能使用已删除、旧版本或失去关系的幽灵 Point；
- 阈值和 Top-K 是否配置化且有合理默认值。

### 3.3 Prompt 与安全

- 个人知识是否作为不可信数据与系统指令隔离；
- 是否可能让笔记中的 prompt injection 覆盖系统行为；
- 是否把秘密、完整 Prompt 或过量私人正文写入日志；
- 上下文长度是否有上限和确定性裁剪策略。

### 3.4 RAG 状态与降级

- NOT_AVAILABLE、NOT_NEEDED、USED、NO_RELEVANT_CONTEXT、DEGRADED 是否语义清楚；
- Router/Embedding/Qdrant 失败是否区分；
- 降级回答是否仍能正确完成 SSE 和消息持久化；
- 失败是否留下足够但安全的诊断信息。

### 3.5 引用诚实性

- retrieved source 是否真的是提供给模型的内容；
- cited source 是否只来自回答中有效的 `[Sx]`；
- 不存在的 source ID 是否被忽略或标记异常；
- 前端是否错误声称来源证明整个回答；
- 历史消息是否展示当时快照，而非重新执行检索；
- 来源链接是否经过 owner 校验。

### 3.6 Eval 与范围

- 固定样例是否同时覆盖 routing、filter、retrieval、source；
- 确定性链路测试是否不依赖云端模型；
- 真实 Eval 是否显式可选且不阻断普通本地验证；
- 是否为追求分数提前加入 Rerank/Hybrid/Workflow。

## 4. 必跑验证

- `scripts/verify-fast.sh`；
- `scripts/verify-integration.sh`；
- 固定 Eval；
- `needRag=false` 的零检索调用断言；
- 跨 owner/跨 KnowledgeBase 隔离测试；
- 旧版本 Point 回查剔除测试；
- Router 非法输出与失败降级测试；
- 不存在 `[Sx]` 的引用测试；
- prompt injection 样例；
- 一个受控真实 Router/RAG smoke test。

## 5. 高风险反例

- Qdrant 只过滤 KnowledgeBase、不顾 userId；
- 直接使用 Qdrant payload 正文，不回 MySQL 校验；
- Router 失败被显示为“不需要 RAG”；
- Qdrant 失败被显示为“没有相关知识”；
- 将所有 retrieved sources 都标为 cited；
- 根据前端当前 Item 状态重建历史引用；
- 模型输出 `[S99]` 后前端伪造一个来源；
- 将知识文本直接拼进 system prompt 指令区；
- 每个 KnowledgeBase 各取 Top-K，导致上下文无上限膨胀。

## 6. Review 输出格式

按 P0/P1/P2/P3 输出 findings，每条包含文件/行、复现路径、影响、违反基线、最小修复和建议测试。

结尾必须分别给出 Spec、Standards、Eval 可信度和是否允许进入 Phase 07。
