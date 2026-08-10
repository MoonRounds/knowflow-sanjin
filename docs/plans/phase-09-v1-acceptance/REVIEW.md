# Phase 09 Review：V1 端到端验收与交付加固

## 1. Review 目标

对 KnowFlow V1 做最终独立验收：既审查全局决策与工程质量，也以真实前端用户路径验证知识能完成“产生 → 沉淀 → 索引 → 检索 → 再利用”。

本 Review 不以新增功能数量为标准，也不在 Review 中顺手实现 Future 能力。

## 2. Review 前置输入

- [全局决策基线](../DECISIONS.md)
- [Phase 09 Plan](./PLAN.md)
- Phase 00–08 的 Plan、Review 结论和修复记录；
- V1 候选 commit；
- `verify-all.sh`、GitHub Actions、Eval、Playwright 和真实模型验收结果；
- V1 验收记录草稿。

## 3. 必查项

### 3.1 Spec 总体一致性

- 是否完整覆盖两个核心闭环；
- 是否所有核心操作都能从正式 Vue 前端完成；
- 是否偷偷重新加入登录、本地模型或其他已排除能力；
- 目录、package、技术栈和依赖是否符合决策基线；
- 是否存在 Phase 间互相矛盾的状态/语义。

### 3.2 数据与隔离

- userId/ownerId 是否贯穿 MySQL、Redis、RabbitMQ 消息和 Qdrant；
- 所有读取和变更是否通过 CurrentOwnerProvider；
- Qdrant 是否始终有 userId/KnowledgeBase filter 和 MySQL 回查；
- ModelConfig API Key、个人正文和原文件是否可能越权或泄漏；
- 软删除、版本切换和异步旧任务是否留下幽灵数据。

### 3.3 核心调用链

- Chat SSE 生命周期、停止和重新生成；
- Conversation 级 Memory 与 MySQL History 的一致性；
- 每轮模型选择和 Revision 快照；
- Router、Retrieval、Prompt、Source trace；
- Extraction cutoff、Candidate 审核和幂等确认；
- Manual/Upload Item 到 Chunk/Embedding/Qdrant；
- 文件去重、解析、下载和来源追溯。

### 3.4 事务、异步与恢复

- 所有 DB → MQ 边界是否可靠；
- Consumer 是否幂等；
- PENDING/PROCESSING/SUCCEEDED/FAILED 是否为唯一业务任务状态；
- TTL Retry Queue/DLX/DLQ 是否符合约定；
- 自动重试是否有限；
- 重启、乱序和重复投递是否安全；
- 用户能否从前端定位并 Retry 核心失败。

### 3.5 前端体验与诚实语义

- 一级导航和正式路由是否符合范围；
- 空、加载、流式、取消、失败、重试状态是否可理解；
- Upload 是否没有被做成无必要一级模块；
- RAG 状态是否区分无需、无结果和降级；
- retrieved/cited 来源是否诚实；
- Processing 是否轻量，没有演化为伪运维平台。

### 3.6 测试、CI 与 Harness

- 本地脚本是否是事实源；
- CI 是否仅调用同一脚本；
- 后端、前端、集成、OpenAPI、Eval、Playwright 是否覆盖实际风险；
- 测试是否依赖固定 sleep 或不稳定真实 LLM；
- 真实模型验收是否独立、可控且不泄密；
- AGENTS/架构/领域/ADR/运行文档是否足以支持新的隔离任务。

### 3.7 安全与部署边界

- 无认证警告是否醒目；
- 默认 bind/deployment 是否不会鼓励裸露公网；
- CORS、文件下载、日志、错误响应和配置样例是否安全；
- `.env`、API Key、数据库密码、本地数据和日志是否被 `.gitignore` 排除；
- 虚拟线程是否仍默认为 false；
- 公网部署认证是否明确标为 Deployment Hardening，而非已经实现。

## 4. 必跑验证

- `scripts/verify-all.sh`；
- GitHub Actions 对候选 commit 的完整结果；
- Playwright 对话沉淀闭环；
- Playwright Manual Note/文件闭环；
- 固定 Router/RAG Eval；
- ModelConfig 密钥泄漏与历史 Revision 检查；
- 跨 owner/KnowledgeBase 隔离检查；
- SSE 停止/重生成检查；
- Extraction 0 Candidate、重复触发、并发确认检查；
- 文件 MIME/哈希/并发去重/下载检查；
- Embedding/Qdrant/RabbitMQ/Parser 故障演练；
- 服务重启恢复；
- 一次受控真实云端模型完整验收。

## 5. 用户现场验收

最终 Review 不能只给自动化结论。用户应亲自完成并能解释：

1. 一条 Assistant Message 如何追溯实际 ModelConfig Revision；
2. Chat History 与 Redis Chat Memory 为什么不同；
3. Candidate 确认如何做到最多创建一个 Item；
4. MySQL 提交成功而 Qdrant 失败时如何恢复；
5. RabbitMQ 为什么会重复投递而 Consumer 仍安全；
6. 文件为何在解析前用 MIME + 原始内容 hash 去重；
7. Qdrant 为何不保存完整正文；
8. Router 如何选择多个 KnowledgeBase；
9. retrieved source 与 cited source 有什么差别；
10. 为什么当前无认证版本不能裸露公网。

这不是口试门槛，而是项目“AI 辅助开发但用户真正理解”的最终目标检查。

## 6. 高风险反例

- E2E 只调用 API，不操作正式前端；
- 只验证成功路径，没有 Retry/DLQ/重启恢复；
- CI YAML 复制一套与本地不同的命令；
- 用真实 LLM 的随机输出作为所有测试硬断言；
- 为让 E2E 通过写固定 sleep 和超长 timeout；
- 前端把检索过的所有来源都称为“引用”；
- 默认配置包含真实 API Key；
- 无认证应用默认鼓励绑定公网地址；
- Phase 09 临时加入 Hybrid Search、MinIO、Security 或 Agent；
- 文档描述与代码状态机不一致。

## 7. Review 输出格式

第一部分按 P0/P1/P2/P3 输出 findings，每条包含证据位置、复现/验证路径、影响、违反的基线、最小修复和缺失测试。

第二部分并列给出：

- Spec Review 结论；
- Standards Review 结论；
- 两个业务闭环结论；
- 失败恢复结论；
- 安全与秘密管理结论；
- 测试/Eval/CI 可信度；
- 文档与可理解性结论。

最终只能给出以下之一：

- `V1 ACCEPTED`：无 P0/P1，两个闭环和恢复验收通过；
- `V1 ACCEPTED WITH FOLLOW-UPS`：无 P0/P1，仅有明确非阻断 P2/P3；
- `V1 NOT ACCEPTED`：存在阻断项，并列出重新 Review 前必须完成的最小修复集合。
