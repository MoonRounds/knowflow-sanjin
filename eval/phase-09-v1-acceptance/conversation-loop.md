# 闭环 1：对话沉淀闭环验收数据

用户通过正式前端把一次会话沉淀为个人知识，并在新会话中检索到该知识。

## 流程

```text
配置并测试 ChatModel
-> 创建 Conversation 并选择默认模型
-> SSE 多轮对话，Chat Memory 保持上下文
-> 用户显式触发截至 cutoff 的会话知识提取
-> 生成 0..N KnowledgeCandidate
-> 用户查看、编辑并确认一个 Candidate
-> 创建 KnowledgeItem
-> 异步 Chunk / Embedding / Qdrant Index
-> 索引成功
-> 新建 Conversation
-> Router 自动判断需要 RAG 并选择 KnowledgeBase
-> 检索刚沉淀的个人知识
-> AI 回答并展示 retrieved/cited sources
```

## 固定数据

- 知识库：`Kf-后端工程规范`（启用，含可检索 Item）
- 会话：`Kf-验收-对话沉淀`
- 默认模型：stub chat（CI）/ 真实 ChatModel（手工验收）

### 会话正文（两轮）

第一轮用户问题：`Kf-海豚-部署前必须备份哪三样东西？`
- 预期 stub 回答（AI 简述 + 模型给出 3 个要点）：
  `海豚部署前必须备份：数据库、配置文件、原始数据。`

第二轮用户问题：`那回滚预案怎么定？`
- 预期 stub 回答：
  `海豚回滚预案：数据库恢复到最近备份，配置回到发布前版本，验证健康检查通过。`

提取范围：截至第二轮完成（cutoff = 第二条 assistant 消息），覆盖全部完整 active Turns。

### 预期提取结果（确定性 stub）

- Extraction Task → SUCCEEDED，产生 1 个 Candidate。
- Candidate 内容包含 `Kf-海豚` 与至少一条「备份」要点。
- 确认后创建 1 个 KnowledgeItem，关联 `Kf-后端工程规范`。
- Item index status 最终为 `INDEXED`。

## 固定问题集（新会话检索）

| 编号 | 问题 | 预期 needRag | 预期选中 KB | 预期来源 |
|------|------|------------|-----------|---------|
| C-Q1 | Kf-海豚-部署前必须备份哪三样东西？ | true | Kf-后端工程规范 | 沉淀 Item |
| C-Q2 | 你好 | false | - | 无检索（NOT_NEEDED） |

## 确定性断言（进 CI）

1. Extraction Task 终态 `SUCCEEDED`；Candidate 数量 1。
2. Candidate confirm 幂等：重复 confirm 不创建第二个 Item。
3. Item `indexStatus=INDEXED` 且 `indexedVersion>=1`。
4. 新会话问题 C-Q1：`ragStatus=USED`，`retrievedSources` 含沉淀 Item，cited 标记正确。
5. C-Q2：`ragStatus=NOT_NEEDED`，无检索来源。
6. 全程 `ownerId=1` 边界：所有创建的 KB/Item/会话/Task 归属 owner 1。

## LLM 质量观察（手工验收，不进 CI）

- 真实模型回答是否引用 `[S1]` 且来源是刚沉淀的海豚部署知识。
- 问题 C-Q1 是否命中 Kf-后端工程规范（而非其它知识库）。
