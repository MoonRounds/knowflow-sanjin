# KnowFlow V1 产品范围

KnowFlow V1 是一个**个人 AI 学习与知识沉淀系统**，帮助用户在与 AI 的对话中提取有价值的知识并建立可检索的个人知识库。

## V1 成功标准

V1 以两个真实前端闭环为完成标准：

### 对话沉淀闭环

1. 配置并选择 ChatModel
2. 通过 SSE 进行多轮聊天
3. 用户显式触发会话级知识提取
4. AI 生成 0～N 个 KnowledgeCandidate
5. 用户审核、编辑、确认 Candidate
6. 生成 KnowledgeItem 并入知识库
7. 异步 Chunk / Embedding / Qdrant Index
8. 新会话中 Router 自动决定是否检索
9. RAG 检索与来源展示

### 个人笔记闭环

1. 创建 KnowledgeBase
2. 创建 Manual Note 或上传 Markdown/TXT 文件
3. 生成 KnowledgeItem
4. 解析 / Chunk / Embedding / Index
5. 新会话中 Router 自动决定是否检索
6. RAG 检索与来源展示

## V1 核心功能

- 多模型配置与管理（OpenAI-Compatible API）
- 多轮 SSE 流式聊天
- Redis 聊天记忆
- 知识库管理与知识条目生命周期
- 对话知识提取与候选审核
- 手动笔记创建
- Markdown/TXT 文件上传与解析
- 异步文档分块与向量索引
- 智能路由与 RAG 检索
- 来源引用展示

## V1 明确不包含

- 用户注册、登录、权限管理
- PDF、Word 等富文档解析
- MCP、Agent、Multi-Agent
- 微服务与分布式部署
- 公网裸部署
- Prometheus/Grafana 全套监控
- GraphRAG、Hybrid Search、高级 Rerank
- MinIO 对象存储

运行与公网部署前置条件见 [V1 已知限制与安全边界](./v1-known-limitations.md)。
