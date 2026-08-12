# V1 已知限制与安全边界

## 安全边界

V1 固定 System Owner `id=1`，没有登录、Session、权限管理或租户认证。它只能运行在：

- localhost；
- 可信内网；
- 或已有外层身份认证、TLS 和网络访问控制的环境之后。

不得把 `5173/8080` 或 MySQL、Redis、RabbitMQ、Qdrant 端口裸露公网。公网部署前至少需要补充
Spring Security 单账户 Session、Secure/HttpOnly/SameSite Cookie、CSRF、防暴力尝试、TLS、反向代理
安全头、Secret Manager、备份/恢复和审计策略。

## 功能限制

- 只支持 OpenAI-Compatible 文本 Chat/Utility；无视觉、Tool Calling、推理过程展示。
- 上传仅支持 UTF-8 `.md/.markdown/.txt`，默认 5 MiB；无 PDF/Word/Tika Parsers。
- 原文件使用单机本地 Volume；无 MinIO、多节点共享存储或公开分享链接。
- 单一固定 dense Embedding profile；无在线模型/维度切换、全量重建 UI、Hybrid Search、Rerank、
  GraphRAG 或手动语义搜索页。
- Redis 故障可由 MySQL 重建 Memory；Qdrant/Utility 故障会降级，但完整个人知识检索不可用。
- 无 Dashboard、完整监控平台、移动端专项、MCP、Agent/Skills、微服务、分布式事务。
- Token Usage 尽力记录，不承诺精确计费；真实 Provider 兼容性需显式 smoke/eval。

## 运维限制

- Docker Compose 面向单机开发/可信环境，不是生产级 HA 编排。
- 本地文件与 MySQL 必须协调备份；只备份数据库不能恢复上传原文件。
- V1 使用有限结构化日志、Actuator health/info 与 correlationId，不含完整指标/链路追踪平台。
- Embedding profile 变更和跨机器迁移需要人工运维；先阅读
  [Qdrant/Embedding 文档](../development/qdrant-embedding.md)与
  [文件存储文档](../development/document-upload-local-storage.md)。
