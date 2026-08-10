# KnowFlow V1 分阶段开发路线图

本文档是 KnowFlow V1 的阶段索引。它只描述执行顺序，不代替各阶段的详细 `PLAN.md`、独立 `REVIEW.md` 或项目决策基线。

## 使用入口

在开始任何阶段前，先阅读：

1. [执行与 Review 工作流](./WORKFLOW.md)
2. [V1 决策基线](./DECISIONS.md)
3. 目标阶段的 `PLAN.md`
4. 仓库根目录 `AGENTS.md`

执行完成后，开启一个新的 Codex 任务，只阅读同一阶段的 `REVIEW.md` 以及它要求的材料。Review 默认只报告问题，不顺手修改代码。修复工作应进入独立任务，修复后重新运行该阶段 Review。

## V1 Definition of Done

KnowFlow V1 是否完成，以两个真实前端闭环为准：

### 对话沉淀闭环

```text
配置并选择 ChatModel
→ SSE 多轮聊天
→ 用户显式触发会话级知识提取
→ AI 产生 0～N 个 KnowledgeCandidate
→ 用户审核、编辑、确认
→ KnowledgeItem
→ 异步 Chunk / Embedding / Qdrant Index
→ 新会话自动 Router
→ RAG 检索与来源展示
```

### 个人笔记闭环

```text
创建 KnowledgeBase
→ 创建 Manual Note 或上传 Markdown/TXT
→ KnowledgeItem
→ 解析 / Chunk / Embedding / Index
→ 新会话自动 Router
→ RAG 检索与来源展示
```

后端 API、Postman 或单元测试跑通都只是开发过程，不等于 V1 产品闭环完成。

## 阶段索引

| Phase | 目标 | 执行计划 | 独立 Review |
|---|---|---|---|
| 0 | 仓库安全与前后端 Walking Skeleton | [PLAN](./phase-00-walking-skeleton/PLAN.md) | [REVIEW](./phase-00-walking-skeleton/REVIEW.md) |
| 1 | System Owner 与 KnowledgeBase 全栈切片 | [PLAN](./phase-01-owner-knowledge-base/PLAN.md) | [REVIEW](./phase-01-owner-knowledge-base/REVIEW.md) |
| 2 | ModelConfig、Revision 与 Provider Compatibility | [PLAN](./phase-02-model-config/PLAN.md) | [REVIEW](./phase-02-model-config/REVIEW.md) |
| 3 | Conversation、Message 与 SSE Streaming | [PLAN](./phase-03-conversation-sse/PLAN.md) | [REVIEW](./phase-03-conversation-sse/REVIEW.md) |
| 4 | Redis Chat Memory 与多轮上下文 | [PLAN](./phase-04-chat-memory/PLAN.md) | [REVIEW](./phase-04-chat-memory/REVIEW.md) |
| 5 | Manual Knowledge 与异步向量索引 | [PLAN](./phase-05-manual-knowledge-indexing/PLAN.md) | [REVIEW](./phase-05-manual-knowledge-indexing/REVIEW.md) |
| 6 | Knowledge Router、RAG 与 Citation | [PLAN](./phase-06-router-rag-citation/PLAN.md) | [REVIEW](./phase-06-router-rag-citation/REVIEW.md) |
| 7 | Conversation Knowledge Extraction | [PLAN](./phase-07-knowledge-extraction/PLAN.md) | [REVIEW](./phase-07-knowledge-extraction/REVIEW.md) |
| 8 | Markdown/TXT Upload 与 Document Processing | [PLAN](./phase-08-document-upload/PLAN.md) | [REVIEW](./phase-08-document-upload/REVIEW.md) |
| 9 | V1 双闭环、完整 Compose 与 E2E 验收 | [PLAN](./phase-09-v1-acceptance/PLAN.md) | [REVIEW](./phase-09-v1-acceptance/REVIEW.md) |

## 顺序约束

- 默认必须按 Phase 0 → 9 顺序执行。
- 一个 Phase 未完成 Review 或存在未关闭的高优先级问题时，不进入下一 Phase。
- 每个 Phase 只引入当前范围真实需要的依赖和容器。
- 若执行中发现必须修改已确认的产品或架构决策，应停止实现，先更新决策基线或新增 ADR，再由项目 Owner 确认。
- 不允许为了未来 Phase 提前创建空接口、空表、通用框架或未使用的依赖。

## 明确不在 V1 的范围

- PDF、Word、完整 Apache Tika Parsers
- 登录、注册、JWT、Session、OAuth2、RBAC
- 公网裸部署
- MinIO
- MCP、Agent、Multi-Agent、Agent Skills
- Workflow/Graph、GraphRAG
- Hybrid Search、高级 Rerank、复杂 Query Rewrite
- 多模型并行比较或 Ensemble
- 微服务、分布式事务、Kubernetes
- Prometheus/Grafana/ELK/OpenTelemetry 全套平台
- Resilience4j；Virtual Threads 在 V1 默认关闭，不做专项启用与验证
