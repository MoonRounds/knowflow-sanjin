# KnowFlow V1 决策基线

本文档汇总编码前已经确认的稳定决策。它是所有 Phase PLAN 与 REVIEW 的共同输入。

## 1. 产品边界

- 产品定位：个人 AI 学习与知识沉淀系统。
- V1 以“知识产生 → 沉淀 → 索引 → 检索 → 再利用”的完整生命周期为成功标准。
- 两个最终闭环：Conversation Knowledge Extraction 和 Manual/Uploaded Note Retrieval。
- V1 前端必须能够真实完成两个闭环。
- RAG 通过固定、可重复 Eval 样例验收，不承诺任意问题 100% 路由命中。

## 2. 用户与部署边界

- V1 是单用户系统，不实现应用内认证。
- MySQL 存在极简 `app_user`，Flyway 初始化 `id=1` 的 System Owner。
- Controller 不接受客户端传入的 userId；统一通过 `CurrentOwnerProvider` 获取。
- 所有业务数据和 Qdrant Payload 保留 ownerId/userId。
- 默认仅允许 localhost 或可信环境部署，宿主机端口默认绑定 `127.0.0.1`。
- 登录、Session、CSRF、HTTPS 和公网部署属于 Deployment Hardening。

## 3. 仓库与工程命名

```text
knowflow-sanjin/
├── pom.xml
├── knowflow-app/
├── frontend/
├── docs/
├── eval/
└── scripts/
```

- Maven 父工程：`knowflow.sanjin:knowflow-sanjin`（`packaging=pom`）。
- 后端子模块：`knowflow.sanjin:knowflow-app`，继承根父 POM。
- Java 根包：`knowflow.sanjin`。
- 前端使用 npm 和 `package-lock.json`。
- 本地 `scripts/` 验证入口是事实源，GitHub Actions 只调用这些脚本。

## 4. 后端技术基线

- Java 21。
- Spring Boot 4.1.x 正式版。
- Spring AI 2.0.x 正式版。
- Maven Wrapper。
- Spring MVC + SSE，不采用全栈 WebFlux/R2DBC。
- `spring.threads.virtual.enabled=false`，V1 不启用 Virtual Threads。
- MyBatis-Plus Boot 4 Starter。
- MySQL 8.4 LTS。
- Flyway 显式 SQL Migration，不使用自动建表或 H2 模拟 MySQL。
- 不使用 Lombok、MapStruct、Resilience4j。
- 后端格式化使用 Spotless + google-java-format。

## 5. 前端技术基线

- Vue 3 + TypeScript + Vite。
- Composition API 与 `<script setup lang="ts">`。
- Vue Router、Element Plus。
- Pinia 只在出现真实跨页面客户端状态时加入，不作为所有服务端数据的默认容器。
- Vitest、Vue 官方 ESLint 配置和 Prettier。
- 后期使用 Playwright 验证核心闭环。
- AI/知识 Markdown 使用受控渲染，禁用未经净化的原始 HTML。

正式一级路由：

- `/chat`
- `/knowledge-bases`
- `/knowledge-items/:id`
- `/candidates`
- `/processing`
- `/model-settings`

Upload 是 Knowledge 流程入口，不是一级导航。

## 6. API 规范

- REST 基础路径 `/api/v1`。
- 成功响应直接返回资源或分页 DTO，不使用全局 `Result<T>`。
- 错误使用 Problem Details，并包含稳定 `errorCode` 与 `correlationId`。
- MySQL BIGINT 在 API/SSE/OpenAPI 中统一序列化为字符串。
- 普通列表使用 page/size；Message History 使用 sequence 游标。
- OpenAPI 是前后端契约来源，前端生成 TypeScript 类型，手写薄 fetch Client。
- 聊天流使用 POST + fetch response stream，不使用原生 EventSource API。

SSE 最小事件：

- `generation.started`
- `generation.stage`
- `content.delta`
- `sources.available`
- `generation.completed`
- `generation.failed`

后台 Processing 状态使用 REST 有限轮询，不建立通用 WebSocket/SSE。

## 7. ChatModel 与模型配置

- V1 只支持 API Key 云端 OpenAI-Compatible 文本 ChatModel。
- 不支持 Ollama、本地模型、视觉、Tool Calling、Function Calling、推理过程展示。
- ModelConfig 基础参数：display/provider name、Base URL、Model Name、temperature、maxOutputTokens、enabled。
- Base URL 默认只允许安全 HTTPS 云端目标，阻止 localhost、回环、私网、内嵌凭据和危险重定向。
- API Key 使用应用级主密钥认证加密，接口只返回掩码。
- ModelConfig 是逻辑配置；不可变 ModelConfigRevision 保存具体参数和加密 Secret。
- Conversation 保存 `defaultModelConfigId`，每次发送可选择其他 ModelConfig 并更新 Conversation 默认值。
- Assistant Message 锁定实际 Revision，并保存非敏感模型快照。
- Owner AI Settings 保存 `defaultChatModelConfigId` 和 `utilityModelConfigId`。
- Router 与 Extraction 共用一个 Utility Model，但任务锁定具体 Revision。
- Utility Model 设置前必须通过结构化输出能力测试。
- V1 至少真实验证两个 Provider，优先 DeepSeek + Qwen。
- Provider 只承诺基础文本、流式和基础参数；未实测服务标为“可能兼容”。
- Token Usage 尽力记录，不承诺精确费用。

## 8. Conversation、Message 与 SSE

- MySQL Message 是 Chat History 事实源。
- User Message 是一轮起点；Assistant Message 本身就是 Generation Attempt。
- Assistant Message 通过 `replyToMessageId` 指向 User Message。
- 一个 User Message 可有多个 Assistant Attempts，但只有一个 completed attempt 为 active。
- 同一 Conversation 同时最多一个 active Generation，正确性由 MySQL 原子状态保证。
- User Message 先落库，Assistant Message 经历 `GENERATING / COMPLETED / FAILED / CANCELLED`。
- 流式 chunk 不逐 token 写 MySQL；成功保存完整内容，失败/取消可保存 partial content。
- 已输出正文后不自动重试流式调用；用户可以重新生成。
- failed/cancelled/abandoned Turn 不进入 Memory 或 Extraction。
- 已提交 Message 不可编辑，不支持会话分支。
- active Generation 存在时禁止删除 Conversation。
- Conversation 软删除，不级联删除已沉淀知识。
- 标题默认取首条 User Message 安全截断，可手动改名，不调用 AI 生成标题。
- `clientMessageId` 防止网络重试重复创建消息。

## 9. Chat Memory

- Chat History ≠ Chat Memory ≠ Knowledge Base。
- Redis Memory 是可丢失、可重建的投影；MySQL 是事实源。
- 使用普通 Redis，不使用 Redis Stack、RedisJSON 或 Query Engine。
- 在 infrastructure 内实现轻量 Spring AI `ChatMemoryRepository`。
- 使用最近 N 个完整 active Turns，默认从 10 轮开始，参数可配置。
- TTL 按不活跃时间刷新，默认从 7 天开始，参数可配置。
- Redis miss/故障时从 MySQL 重建。
- 模型切换不重置 Conversation Memory。
- V1 不做摘要 Memory。

## 10. Knowledge 领域

- KnowledgeBase 是逻辑知识域，不对应物理数据库或 Collection。
- 同一 Owner 下 KnowledgeBase 规范化名称唯一。
- KnowledgeBase 可禁用；禁用不删除 Item 或向量，但不进入 Router。
- 删除 KnowledgeBase 不级联删除 Item；若删除会导致 Item 零归属则阻止。
- KnowledgeItem 必须关联 1～N 个 KnowledgeBase 才能进入索引生命周期。
- KnowledgeItem 与 KnowledgeBase 多对多。
- Tag 是 Owner 级轻量实体，与 Item 多对多；Tag 可选并规范化去重。
- SourceType：`AI_CONVERSATION / MANUAL_NOTE / UPLOAD_FILE`。
- 规范正文统一保存 UTF-8 Markdown。
- Manual/Candidate Item 正文可编辑；Upload Item 正文只读。
- Manual Note 无 Draft；保存即创建 ACTIVE Item 和 Index Task。
- 已索引 Item 可修改，并区分 `contentVersion` 与 `indexedVersion`。
- Item lifecycle：`ACTIVE / DELETING / DELETED`。
- Item index status：`PENDING / PROCESSING / INDEXED / FAILED`。
- 旧索引在新版本成功前继续服务；新版本失败时旧索引仍可用。
- 支持单 Item 强制 Reindex。
- 删除 Item 异步清理 Qdrant；检索后必须校验 MySQL 状态。
- 用户可编辑核心记录使用 rowVersion 乐观锁。

## 11. Candidate 与 Extraction

- 用户显式点击“提取知识”，不是每轮回答后自动运行。
- 提取范围是当前 Conversation 截至 cutoffMessageId 的全部完整 active Turns。
- Task 保存范围、hash 和版本，不复制整份 Conversation 文本。
- 超过可配置输入预算时明确拒绝，不静默截断，不做 Map-Reduce。
- 相同 Owner、Conversation、cutoff、Extraction Profile、Utility Revision 只创建一个 Task。
- 一次 Extraction 返回 0～10 个 Candidate，默认上限 10，可配置。
- Candidate 保存 AI 原结果、用户编辑草稿、来源范围和审核状态。
- Candidate：`PENDING / CONFIRMED / REJECTED`。
- REJECTED 可以恢复为 PENDING；CONFIRMED 是终态。
- Candidate 确认后最多创建一个 Item，确认必须幂等。
- Item 的 Candidate 来源可为空；Manual Note 和 Upload 直接创建 Item。
- AI 只能建议已有 KnowledgeBase，不能自动创建。

## 12. Embedding、Chunk 与 Qdrant

- ChatModel 与 EmbeddingModel 完全分离。
- Embedding 是单一系统级云端配置，不进入用户 ModelConfig 页面。
- 首选 Spike：Qwen `text-embedding-v4`、1024 维、dense vector。
- Embedding Profile 与 Chunking Profile 有显式版本。
- V1 不在线切换 EmbeddingModel；更换需要未来全量重建流程。
- Qdrant 使用按 Embedding Profile 划分的共享 Collection，不按 Owner/KnowledgeBase 建 Collection。
- Point metadata 至少包含 ownerId、itemId、chunkId、knowledgeBaseIds、content/index version、chunkIndex、sourceType、Tags、profile versions。
- Qdrant 不保存完整 Chunk 正文；命中后从 MySQL 批量回表。
- MySQL `knowledge_chunk` 保存规范 Chunk 正文和关系。
- Point ID 由 Owner、Item、contentVersion、chunkIndex 确定性生成 UUID。
- Embedding 输入是 Item title + heading path + chunk body。
- Tags、KnowledgeBase 和 Summary 不拼入 Embedding 文本。
- 内容/标题变化重新 Chunk/Embedding；关系、Tags、Summary 变化只更新 Payload。
- Markdown 采用轻量结构感知 Chunking；TXT 按段落；过大块再按字符数拆分并保留少量 overlap。
- V1 不引入模型专用 tokenizer、语义 Chunking 或 LLM Chunking。

## 13. Router、RAG 与 Citation

- Router 使用 Utility Model Structured Output。
- 输入包含当前问题、少量最近上下文和全部可路由 KnowledgeBase 的 id/name/description。
- 只提供 enabled 且至少有一个当前可检索 Item 的 KnowledgeBase。
- 无可用知识库时跳过 Utility 调用并标记 `RAG_NOT_AVAILABLE`。
- Router 输出 `needRag`、0～3 个 KnowledgeBase、route scores、`retrievalQuery`。
- route score 只用于 Trace/Eval，不作为校准概率或硬阈值。
- Router 非法输出最多修复一次；仍失败则普通回答并标记 `RAG_DEGRADED`。
- 多知识库使用 OR metadata filter 和一次全局 Top-K。
- Vector threshold/Top-K 配置化并由 Eval 校准。
- 无足够相关结果时普通回答并标记 `RAG_NO_RELEVANT_CONTEXT`。
- RAG 状态：`NOT_AVAILABLE / NOT_NEEDED / USED / NO_RELEVANT_CONTEXT / DEGRADED`。
- RAG Context 是不可信数据，不能覆盖系统指令。
- `retrievedSources` 与 `citedSources` 分离。
- 模型使用 `[S1]` 等编号；后端只接受本次检索集合内的 Citation。
- Generation Trace 保存 Router 结果、retrievalQuery、Chunk rank/score、cited 标识和 Prompt/Profile 版本，不默认保存完整 Prompt。
- V1 不提供手动知识库覆盖或独立语义搜索页面。

## 14. Document 与文件

- V1 只支持 `.md`、`.markdown`、`.txt`。
- 只接受 UTF-8，可含 BOM；其他编码明确拒绝。
- 默认最大 5 MiB，限制可配置。
- 使用 `tika-core` 做 MIME/文本检测，不使用完整 Tika Parsers。
- 使用 `commonmark-java` 解析 Markdown AST。
- 原文件字节不修改；规范正文移除 BOM 并把 CRLF/CR 统一为 LF。
- 文件按 `ownerId + normalizedDetectedMimeType + SHA-256(raw bytes)` 精确去重；文件名不参与唯一键。
- ACTIVE 重复文件复用已有 Item；DELETED 重复文件视为恢复。
- 一文件对应一个 FileMetadata 和一个 KnowledgeItem。
- 上传接口只保存文件和创建 Task；解析与索引是两个异步阶段。
- 文件使用本地持久化目录和 Docker Volume；未来才迁移 MinIO。
- 原文件可经安全后端接口下载，不暴露真实路径或公开分享链接。
- 上传文件不自动调用 AI 生成摘要；标题优先取 Markdown H1，否则取文件名。

## 15. Processing、RabbitMQ 与一致性

- RabbitMQ 不进入实时 Chat 主链路。
- 三个逻辑工作队列：Extraction、Document Parsing、Knowledge Indexing。
- MySQL `processing_task` 是任务事实源和轻量 Outbox。
- 业务事务同时写业务状态与 PENDING Task；提交后发布 taskId 小消息。
- 恢复扫描重新发布未投递/滞留 PENDING Task。
- ProcessingTask 统一保存 taskType、business key/version、状态、retry、delivery、last error 和 immutable input snapshot。
- 状态仅为 `PENDING / PROCESSING / SUCCEEDED / FAILED`。
- 自动重试累计在原 Task；最终失败为 FAILED 并进入 DLQ。
- 手动重试创建新 Task，并通过 retryOfTaskId 关联原失败任务；并发或重复点击不得创建多个活动重试 Task。
- RabbitMQ 使用少量固定 `10s / 1m / 5m` TTL Retry Queues + DLX/DLQ，具体值配置化。
- 禁止 `requeue=true` 无限即时重投。
- Consumer 必须幂等，不依赖 exactly-once。
- 不建立 TaskAttempt 表；累计次数和最后错误在 MySQL，详细尝试在结构化日志。
- 删除 Item 时旧 Index Task 可以 `SUCCEEDED + SKIPPED_SUPERSEDED` 结束。

## 16. 失败、依赖与可观测性

- MySQL 是唯一强制启动依赖。
- Redis 故障：从 MySQL 构造 Memory，并显示 degraded。
- RabbitMQ 故障：Task 留在 MySQL 等待恢复发布。
- Qdrant 故障：Chat `RAG_DEGRADED`；Index Task 重试。
- Utility Model 故障：Router 显式降级；Extraction Task 失败。
- Embedding 故障：Index Task 重试。
- ChatModel 故障：Generation 失败。
- 实时 Chat、Utility、Embedding 分别设置简单可配置并发上限。
- V1 使用 Actuator health/info、结构化日志、correlationId 和稳定 error code。
- 默认日志不记录 API Key、完整聊天、知识正文或完整 Prompt。
- Actuator 不公开 env、configprops、heapdump、loggers 等敏感端点。

## 17. 测试与 Harness

- 默认测试/CI 不调用真实云端模型。
- 本地 OpenAI-Compatible Stub 提供普通回复、SSE、Structured Output、Embedding 和错误场景。
- MySQL、Redis、RabbitMQ、Qdrant 使用 Testcontainers。
- 数据库集成测试不使用 H2。
- Live Provider Smoke 和真实模型 Eval 显式运行，普通 PR 不读取真实 Secret。
- Eval 从首个 Router/Retrieval 切片开始建设。
- Prompt 模板作为代码进入 Git，并有 profileVersion；不建设动态 Prompt 管理。
- 后端 API Entity 不直接暴露为 DTO；使用显式 Assembler，不使用 MapStruct。
- 模块使用务实分层，不套完整 DDD/六边形模板，不为每个 Service 建 Interface。
- 不“事件化一切”；同步模块调用显式，异步边界只用 ProcessingTask + RabbitMQ。

## 18. Future Considerations

- Spring Security 单账户登录、Session、HttpOnly Cookie、CSRF。
- MinIO、PDF/Word、完整 Tika Parsers。
- MCP Client/Server。
- Single Agent、Tools、Skills、Multi-Agent。
- Workflow/Graph、GraphRAG。
- Hybrid Search、Rerank、复杂 Query Rewrite。
- Prompt Registry、A/B Test。
- 独立语义搜索、数据统计和复杂 Dashboard。
- Embedding Profile 全量迁移、备份、永久擦除和归档。
- Prometheus/Grafana/OpenTelemetry、Resilience4j、Virtual Threads 的启用与专项验证。
- 微服务、Kubernetes、分布式事务。

## 19. 决策变更规则

- 只修正文案或实现细节：更新相关 PLAN。
- 改变跨 Phase 的稳定决策：更新本文并新增 ADR。
- 引入新框架、中间件或外部服务：必须说明真实问题、简单替代、复杂度和收益，并由 Owner 确认。
- 不允许执行 Agent 在实现中静默改变本文。
