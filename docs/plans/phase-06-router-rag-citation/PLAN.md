# Phase 06：Knowledge Router、RAG 与诚实引用

## 1. 阶段目标

让新的 AI 对话能够自动判断是否需要个人知识、选择 0～N 个相关 KnowledgeBase、检索 Phase 05 已索引的 Manual Note，并在回答中展示真实使用的知识来源。

本阶段第一次打通“已有个人知识 → 新对话自动再利用”的纵向链路。

## 2. 开始条件

- Phase 05 已完成并通过独立 Review；
- 至少有一条可稳定重复索引的 Manual Note；
- Qdrant payload 包含 owner、KnowledgeBase、Item、Chunk、版本信息；
- Chat SSE 与消息持久化已经稳定；
- Utility ModelConfig 能力已可用。

## 3. 本阶段范围

### 3.1 Knowledge Router

- 基于 LLM Structured Output；
- 输出至少包含：
  - `needRag`；
  - 0～N 个 `knowledgeBaseIds`；
  - 可选 route score/reason，主要用于诊断；
- 仅向 Router 提供当前 owner 下、启用且可检索的 KnowledgeBase 目录；
- 最多选择 3 个 KnowledgeBase；
- 使用当前问题与必要的会话上下文形成路由输入；
- 严格校验 Structured Output；
- 首次格式错误允许一次受控修复，之后进入可见降级；
- `needRag=false` 时不访问 Embedding/Qdrant。

### 3.2 Retrieval

- 对检索查询生成 Embedding；
- Qdrant filter 至少包含 `userId` 与 Router 选中的 KnowledgeBase IDs；
- 多知识库采用 OR 语义；
- 使用全局 Top-K，不为每个知识库机械取一套 Top-K；
- Qdrant 返回候选 ID 和 score 后，从 MySQL 批量加载规范 Chunk；
- MySQL 再校验 owner、当前内容版本、Item 可用状态和 KnowledgeBase 关系；
- 低于阈值或无有效候选时不把内容注入 Prompt；
- 为上下文建立清晰的 `[S1]`、`[S2]` 等来源标识。

### 3.3 RAG 生成与状态

每次 Assistant 生成应记录可追溯的 RAG 状态，至少区分：

- `NOT_AVAILABLE`：没有可用知识库或基础能力；
- `NOT_NEEDED`：Router 判断无需 RAG；
- `USED`：检索到上下文并注入生成；
- `NO_RELEVANT_CONTEXT`：需要 RAG 但没有足够相关内容；
- `DEGRADED`：Router、Embedding 或 Retrieval 失败后降级为普通聊天。

不得将失败静默伪装成“没有相关知识”。降级可以继续回答，但前端和追踪数据必须诚实表达状态。

### 3.4 引用语义

- 前端展示的是“本次生成实际提供给模型的 retrieved sources”；
- 只有模型回答中明确引用 `[Sx]` 时，才标记为 cited source；
- 不声称引用证明模型的每句话都由来源支持；
- 不虚构不存在的 source；
- Source 返回 KnowledgeItem title、sourceType、必要摘要/片段和 Item Detail 链接；
- Upload 文件来源将在 Phase 08 补充；
- 历史消息可追溯当次 generation 的检索结果和引用关系。

### 3.5 前端

- Chat Workspace 展示 RAG 状态；
- 展示 Router 选择的知识库（诊断信息可折叠）；
- 展示 retrieved sources 与 cited sources；
- 来源可进入 KnowledgeItem Detail；
- 降级、无相关内容和无需 RAG 的文案必须区分；
- 不提供手动知识库选择器作为 V1 主流程。

### 3.6 Eval

- 在 `eval/` 建立小型、固定、可重复的 V1 数据集；
- 至少覆盖 `needRag`、多知识库路由、metadata filter、retrieval、来源返回；
- Eval 允许模型结果存在合理波动，不以任意问题 100% 命中为目标；
- 为确定性测试使用 Router/Embedding/ChatModel stub；
- 为真实质量检查提供显式、可选的云端模型 smoke/eval 命令。

## 4. 明确不做

- Hybrid Search；
- Rerank；
- 复杂 Query Rewrite；
- 多轮 Retrieval Loop；
- Workflow/Graph；
- Agent；
- 用户手动指定 KnowledgeBase 的替代主流程；
- 基于引用的严格事实验证；
- Knowledge Extraction；
- 文件上传。

## 5. 调用链

```text
User Message
  -> resolve actual ChatModel
  -> build routing query from current turn + necessary memory
  -> Knowledge Router
  -> if needRag: query embedding
  -> Qdrant search with owner/base filters
  -> MySQL load and post-validate current chunks
  -> assemble untrusted personal-knowledge context with [Sx]
  -> ChatModel streaming generation
  -> persist Assistant Message, RAG trace and sources
  -> SSE completion event with source summary
```

个人知识内容必须作为“不可信数据”与系统指令清楚分隔，防止笔记中的文本被当作系统命令执行。

## 6. 实施步骤

### Step A：Router 契约与确定性测试

- 定义内部 Router 输入/输出；
- 获取可路由 KnowledgeBase 目录；
- Structured Output schema、校验与一次修复；
- 0、1、多知识库路由测试；
- 普通问候无需 RAG 的测试；
- Router 失败的 DEGRADED 测试。

### Step B：Retrieval 与 MySQL 回查

- Query Embedding；
- owner + knowledgeBaseIds filter；
- 全局 Top-K 和阈值；
- MySQL 批量回查规范 Chunk；
- 版本、状态、关系二次校验；
- 无效/过期 Qdrant Point 不得进入 Prompt；
- 检索 trace 持久化。

### Step C：RAG Prompt、Streaming 与引用

- 将来源编号为 `[Sx]`；
- 明确模型仅在实际使用来源时引用编号；
- SSE 与数据库记录 retrieved/cited sources；
- 生成中断和失败时保存正确的 RAG trace；
- 不把完整内部 Prompt 或秘密写入日志。

### Step D：前端来源体验

- RAG 状态；
- retrieved/cited source 列表；
- KnowledgeItem 链接；
- NOT_NEEDED、NO_RELEVANT_CONTEXT、DEGRADED 的清晰差异；
- 历史 Assistant Message 可重新展示当时来源。

### Step E：Eval 与验收样例

- 固定知识样本；
- 固定问题和预期 route/filter/source；
- 确定性链路测试；
- 可选真实 LLM eval；
- 记录当前基线结果，不为提升分数提前加入复杂检索能力。

## 7. 测试要求

至少覆盖：

- `needRag=false` 不调用 Embedding/Qdrant；
- 选择 0、1、多个 KnowledgeBase；
- 最多 3 个 KnowledgeBase；
- 非法 KnowledgeBase ID 被拒绝；
- Router 非法输出的一次修复和最终降级；
- owner filter 防止跨 owner 数据泄漏；
- KnowledgeBase OR filter；
- Qdrant 过期版本被 MySQL 回查剔除；
- Item 已删除或关系已移除时不进入 Prompt；
- 无结果和低分结果；
- Embedding/Qdrant 临时失败时状态为 DEGRADED；
- retrieved 与 cited 的区别；
- 模型返回不存在 `[S99]` 时不得生成虚假来源；
- 笔记内 prompt injection 文本不会改变系统行为；
- 历史消息来源可追溯；
- 固定 Eval 样例可重复执行。

## 8. 阶段验收

通过前端完成：

1. 使用 Phase 05 创建并索引一条专有 Manual Note；
2. 新建 Conversation；
3. 提出一个固定验收问题；
4. Router 自动判断需要 RAG 并选中正确 KnowledgeBase；
5. 检索结果满足 owner 与 KnowledgeBase metadata filter；
6. AI 基于该知识回答；
7. 前端展示实际 retrieved source；
8. 若回答含 `[Sx]`，前端标记对应 cited source；
9. 点击来源进入正确 KnowledgeItem；
10. 再发送“你好”，确认不访问知识库并显示 NOT_NEEDED；
11. 注入 Retrieval 故障，确认普通聊天可继续但明确显示 DEGRADED。

## 9. 阶段交付物

- Router、Retrieval、RAG orchestration；
- generation RAG trace/source 数据模型；
- Chat 前端 RAG 与 Sources 展示；
- 固定 Eval 数据集和运行说明；
- 集成测试与真实 smoke/eval 记录；
- 必要架构、领域和 ADR 更新。

## 10. 完成后动作

1. 运行快速与集成验证；
2. 运行固定 Eval；
3. 运行可选真实模型 smoke/eval；
4. 提交本阶段代码；
5. 在全新 Codex 任务中依据 [REVIEW.md](./REVIEW.md) 独立 Review；
6. Review 通过后再进入 Phase 07。
