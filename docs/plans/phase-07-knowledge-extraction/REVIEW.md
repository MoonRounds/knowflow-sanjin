# Phase 07 Review：会话知识提取与人工确认

## 1. Review 目标

确认会话提取是用户显式触发、范围固定、异步可重试且候选必须经过人工确认；重点检查 cutoff、幂等、状态迁移和确认事务。

## 2. Review 前置输入

- [全局决策基线](../DECISIONS.md)
- [Phase 07 Plan](./PLAN.md)
- Phase 07 起止提交；
- 提取 schema/profile 说明；
- 自动化和真实 smoke test 结果。

## 3. 必查项

### 3.1 提取范围

- 是否只有用户点击才触发；
- cutoffMessageId 是否在触发时固定；
- 是否只读取截至 cutoff 的已完成消息；
- 生成中、失败、取消的消息是否被排除；
- 后续新增消息是否污染旧任务；
- 超长输入是否明确拒绝，而非静默截断。

### 3.2 任务与幂等

- 幂等键是否包含 owner/conversation/cutoff/profile/revision；
- 重复或并发点击是否产生重复任务/候选；
- 任务是否错误复制完整 Conversation；
- 0 Candidate 是否正确标记为成功；
- 是否限制最多 10 个；
- Retry 是否复用同一业务任务语义。

### 3.3 AI 输出边界

- Structured Output 是否严格校验；
- AI 推荐的 KnowledgeBase 是否校验 owner 和存在性；
- 是否允许 AI 自动创建 KnowledgeBase；
- 是否把模型输出直接当可信 HTML/命令；
- 原始 AI 结果是否长期保留且与用户草稿分离。

### 3.4 Candidate 状态

- 每个 Candidate 是否独立审核；
- 合法状态迁移是否明确；
- 已确认 Candidate 是否还能被普通编辑造成 Item 隐式变化；
- 拒绝是否绝不创建 Item；
- 恢复拒绝是否只回到待审核。

### 3.5 确认事务

- Candidate → Item 是否数据库层保证 0..1；
- 并发确认是否只创建一个 Item；
- 重复确认是否返回同一个 Item；
- 用户最终草稿是否成为 Item 内容；
- Item、关系、ProcessingTask 和可靠消息是否处于正确事务边界；
- 失败是否可能留下“Candidate 已确认但 Item 不存在”等半状态；
- 是否复用既有索引链路，而非复制实现。

### 3.6 前端

- Chat 是否展示提取触发和异步状态；
- 0 Candidate 是否被当作正常完成；
- Candidates 页面是否同时展示原始值和草稿；
- 双击确认是否有客户端保护且服务端仍幂等；
- FAILED 是否能从 Processing 定位和 Retry。

## 4. 必跑验证

- `scripts/verify-fast.sh`；
- `scripts/verify-integration.sh`；
- cutoff 后新增消息测试；
- 生成中消息排除测试；
- 0/1/N Candidate 测试；
- 超长输入零 LLM 调用测试；
- 重复与并发触发测试；
- 并发确认测试；
- 确认事务回滚测试；
- 拒绝/恢复状态测试；
- 提取失败 Retry/DLQ 测试；
- 确认后 Item 索引与后续 RAG 验收。

## 5. 高风险反例

- 每条 Assistant Message 完成后自动提取；
- Worker 执行时按“当前所有消息”读取，导致 cutoff 漂移；
- 直接截断长会话却不告知用户；
- 0 Candidate 被标成失败并反复重试；
- 重试每次再插入一套 Candidate；
- Candidate 原始值被编辑覆盖；
- 仅靠前端禁用按钮保证确认幂等；
- Candidate 先标确认，再在另一个事务创建 Item；
- AI 提供任意 knowledgeBaseId 后未经 owner 校验直接关联。

## 6. Review 输出格式

按 P0/P1/P2/P3 给出 findings，必须包含证据、复现路径、影响、违反的决策、最小修复和回归测试。

最后分别判断 Spec、Standards、幂等/事务可信度，以及是否允许进入 Phase 08。
