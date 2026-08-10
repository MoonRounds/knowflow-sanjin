# Phase 07：会话知识提取与人工确认

## 1. 阶段目标

实现 KnowFlow 核心 Human-in-the-loop 链路：用户显式点击“提取知识”，系统异步分析当前 Conversation 截至触发时的全部已完成消息，生成 0～N 个 KnowledgeCandidate；用户逐个编辑、确认或拒绝；确认后创建 KnowledgeItem 并复用 Phase 05 的异步索引链路。

## 2. 开始条件

- Phase 06 已完成并通过独立 Review；
- Conversation 消息事实模型稳定；
- Utility ModelConfig 可调用 Structured Output；
- ProcessingTask、RabbitMQ 重试和 KnowledgeItem 索引链路已稳定；
- Candidate 确认可复用 Manual Note 的知识创建与索引能力，而不是复制另一套流程。

## 3. 本阶段范围

### 3.1 显式会话级提取

- Chat Workspace 提供“提取知识”按钮；
- 每次提取范围固定为当前 Conversation 截至触发时的全部已完成消息；
- 记录截止 `messageId`；
- 不包含仍在流式生成、失败、取消或未完成的消息；
- 不提供任意勾选消息范围；
- 输入超过 V1 明确上限时直接拒绝并给出可理解错误，不进行隐式截断或总结。

### 3.2 KnowledgeExtractionTask

- 异步执行；
- 一次任务返回 0～N 个 Candidate，V1 上限 10 个；
- 返回 0 是成功结果，不是失败；
- 幂等键至少包含 owner、conversation、cutoffMessageId、提取 profile/version 和 Utility ModelConfig Revision；
- 重复点击返回同一任务或明确的已有结果，不重复制造候选；
- 任务只保存必要快照和引用，不复制整份 Conversation 正文；
- 提取任务使用独立消息类型/队列，但复用统一 ProcessingTask、Retry 和 DLQ 机制。

### 3.3 KnowledgeCandidate

- Candidate 作为 AI 提取与人工审核记录长期保留；
- 保存 AI 原始提取结果；
- 保存用户最终编辑草稿；
- 推荐字段：title、summary、content、knowledgeBaseIds、tags、source information；
- AI 只能推荐已存在且属于当前 owner 的 KnowledgeBase；
- Candidate 独立审核；
- 支持查看、编辑、确认、拒绝；
- 拒绝后可查看历史，是否允许恢复应采用已确认的简单状态迁移；
- 不支持合并/拆分 Candidate。

### 3.4 Candidate 确认

- Candidate → KnowledgeItem 为 `0..1`；
- KnowledgeItem 的 Candidate 来源关系可选；
- 每个 Candidate 最多创建一个 KnowledgeItem；
- 确认操作必须幂等并能抵御并发双击；
- 确认时保存用户最终编辑内容，而非只保存 AI 原始内容；
- 确认事务内创建 KnowledgeItem、KnowledgeBase 关系和索引 ProcessingTask/可靠消息；
- Candidate 确认成功后可跳转 KnowledgeItem Detail；
- 拒绝不创建 KnowledgeItem。

### 3.5 前端

- Chat Workspace 触发提取并展示任务状态；
- 独立 Candidates 一级页面；
- 查看 AI 原始结果和用户编辑草稿；
- 逐个编辑、确认、拒绝；
- 任务产生 0 个候选时给出明确完成提示；
- FAILED 提取任务也应出现在轻量 Processing 页面并可 Retry；
- Candidate 确认后可追溯到 Item。

## 4. 明确不做

- 每轮聊天自动提取；
- 定时自动提取；
- 任意选择消息范围；
- AI 自动把 Candidate 直接写入知识库；
- 自动创建 KnowledgeBase；
- Candidate 合并、拆分或复杂批量编辑；
- 对候选自动去重/语义合并的复杂算法；
- 文件上传；
- Agent/Workflow。

## 5. 关键状态与约束

状态名称应在编码前用领域文档最终固定，但必须表达：

- 待审核；
- 已确认；
- 已拒绝。

如果允许“撤销拒绝”，只能回到待审核；已确认 Candidate 不得再次生成第二个 Item，也不得通过普通编辑悄悄修改已创建 Item。

Conversation 即使后续新增消息，旧任务的 cutoff 范围也保持不变。后续对更大 cutoff 再触发属于一个新提取任务。

## 6. 实施步骤

### Step A：任务边界与 cutoff 快照

- 定义已完成消息范围；
- 触发时在事务中确定 cutoffMessageId；
- 建立 KnowledgeExtractionTask 与幂等约束；
- 超长输入前置校验；
- 重复点击和并发请求测试。

### Step B：异步 Structured Extraction

- 根据 cutoff 从 MySQL 读取消息；
- 构建可解释的提取 Prompt；
- Structured Output schema；
- 严格校验 0～10 candidates；
- 校验推荐 KnowledgeBase 所有权和存在性；
- 无候选也标记任务成功；
- 错误分类、有限 Retry 和 FAILED/DLQ。

### Step C：Candidate 审核 API

- Candidate 列表、详情；
- AI 原始结果与 editable draft 分离；
- 更新草稿；
- 拒绝及可选恢复；
- 状态迁移和并发版本保护。

### Step D：幂等确认与 KnowledgeItem

- 数据库唯一约束保证每 Candidate 最多一个 Item；
- 确认事务创建 Item、关系、任务和可靠消息；
- 重复确认返回同一个 Item；
- 失败回滚不留下半确认状态；
- 索引完全复用 Phase 05。

### Step E：前端闭环

- Chat 提取按钮、cutoff/状态提示；
- Candidates 页面；
- 编辑、确认、拒绝；
- 0 Candidate 成功结果；
- FAILED Retry；
- 确认后 Item Detail 与索引状态。

## 7. 测试要求

至少覆盖：

- 提取范围只含 cutoff 前已完成消息；
- 正在生成的 Assistant Message 不进入输入；
- 新消息不会改变旧任务范围；
- 0、1、多个 Candidate；
- 超过 10 个的非法模型输出；
- Structured Output 非法；
- 输入超长直接拒绝且无 LLM 调用；
- 重复点击和并发点击的幂等性；
- Utility Model Revision 改变后产生新的任务身份；
- AI 推荐不存在或其他 owner 的 KnowledgeBase；
- Conversation 软删除或状态变化时任务行为明确；
- Candidate 编辑保留 AI 原始值；
- 并发确认只创建一个 Item；
- 确认事务失败不会留下半成品；
- 重复确认返回同一 Item；
- 拒绝不创建 Item；
- 拒绝恢复的合法/非法状态迁移；
- 提取失败的 Retry 和最终 DLQ；
- 确认后索引链路成功。

## 8. 阶段验收

通过前端完成：

1. 在一个 Conversation 中完成多轮学习对话；
2. 点击“提取知识”；
3. 查看异步任务及记录的 cutoffMessageId；
4. 得到 0～N 个 Candidate；
5. 打开一个 Candidate，同时看到 AI 原始结果和编辑草稿；
6. 修改标题、正文、KnowledgeBase 或 tags；
7. 确认 Candidate；
8. 多次点击确认仍只得到一个 KnowledgeItem；
9. 查看 Item 异步索引成功；
10. 新建 Conversation，通过 Phase 06 的自动 RAG 找到刚沉淀的知识；
11. 另一个 Candidate 被拒绝后不产生 Item；
12. 一个“没有值得沉淀内容”的会话返回 0 Candidate 且任务成功。

## 9. 阶段交付物

- KnowledgeExtractionTask 与 Candidate 数据模型；
- 异步提取 Consumer；
- Candidate 审核与幂等确认 API；
- Chat 触发入口和 Candidates 页面；
- 完整测试；
- 提取 Prompt/schema 版本说明；
- 领域与运行文档更新。

## 10. 完成后动作

1. 运行快速、集成和固定 Eval；
2. 执行真实会话提取 smoke test；
3. 提交本阶段代码；
4. 在全新 Codex 任务中依据 [REVIEW.md](./REVIEW.md) 独立 Review；
5. Review 通过后再进入 Phase 08。
