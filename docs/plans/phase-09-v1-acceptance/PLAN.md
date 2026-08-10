# Phase 09：V1 端到端验收与交付加固

## 1. 阶段目标

不再增加新的产品架构能力，而是把 Phase 00–08 已完成的能力整合成用户可以通过正式前端操作的两个端到端知识闭环，并建立可重复的 V1 验收、失败演练、CI 和运行文档。

V1 的完成依据是知识生命周期闭环，而不是模块数量。

## 2. 开始条件

- Phase 00–08 全部完成并分别通过独立 Review；
- 所有 P0/P1 问题已解决；
- 每个阶段的 migration、OpenAPI、测试和文档均已合入；
- 本地 Docker Compose 能启动 MySQL、Redis、RabbitMQ、Qdrant 和应用所需本地存储；
- 至少配置一个真实 ChatModel、一个 Utility Model 和固定 EmbeddingModel。

## 3. 本阶段范围

### 3.1 正式前端产品闭环

一级导航保持：

- Chat；
- Knowledge；
- Candidates；
- Processing；
- Settings。

正式路由至少包括：

- `/chat`；
- `/knowledge-bases`；
- `/knowledge-items/:id`；
- `/candidates`；
- `/processing`；
- `/model-settings`。

Upload 仍从 Knowledge 流程进入；KnowledgeItem Detail 不是一级导航。处理空状态、加载状态、流式状态、失败状态和可访问的基础交互，但不建设 Dashboard、运营后台或移动端专项版本。

### 3.2 对话沉淀闭环

必须通过前端完成：

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

### 3.3 个人笔记闭环

必须通过前端分别验证 Manual Note 和至少一种上传文件：

```text
创建 KnowledgeBase
-> 创建 Manual Note 或上传 Markdown/TXT
-> 形成 KnowledgeItem
-> 文档场景异步解析
-> Chunk / Embedding / Qdrant Index
-> 索引成功
-> 新建 Conversation
-> Router 自动路由
-> RAG 检索个人笔记
-> AI 回答
-> 前端追溯 KnowledgeItem / 文件来源
```

### 3.4 失败状态与恢复验收

至少演练：

- Knowledge Extraction 失败；
- Document Processing 失败；
- Embedding 失败；
- Qdrant Index 失败；
- RabbitMQ Consumer 达到最大重试；
- 服务在 PENDING/PROCESSING 中途重启；
- 用户手动 Retry；
- 最终 DLQ 与 MySQL FAILED 状态一致；
- SSE 中断、停止生成和重新生成。

失败注入应使用测试替身或明确的开发配置，不通过修改生产逻辑制造不可维护分支。

### 3.5 自动化验收

- 后端 compile/test；
- 前端 typecheck/test/build；
- lint/formatting；
- Testcontainers 集成测试；
- OpenAPI contract/client generation validation；
- 固定 Router/RAG Eval；
- Playwright 覆盖两个核心前端闭环的稳定 happy path；
- 少量关键失败路径 E2E；
- `scripts/verify-all.sh` 作为本地完整事实源；
- GitHub Actions 调用相同仓库脚本。

真实云端模型测试因费用、密钥和不确定性，不应成为每个 PR 的默认硬阻断；应提供显式的受控验收命令和结果记录。

### 3.6 文档与可维护性

- 根 README：定位、架构、启动、验证、安全警告；
- `AGENTS.md`：AI 协作规则和验证入口；
- 架构总览与核心调用链；
- 领域模型与状态机；
- ADR 索引；
- 本地配置示例，不含秘密；
- Docker Compose 使用与数据目录；
- RabbitMQ Retry/DLQ 和手动恢复；
- Qdrant collection/Embedding 维度说明；
- Eval 数据集和执行说明；
- V1 已知限制与 Future Considerations；
- 面试/学习导向的核心链路阅读指引可以作为补充，但不替代技术文档。

## 4. 明确不做

Phase 09 不允许借“验收优化”加入：

- PDF/Word；
- MinIO 实现；
- 登录、注册、OAuth2、RBAC；
- MCP；
- Agent/Multi-Agent/Skills；
- Workflow/Graph；
- GraphRAG；
- Hybrid Search、高级 Rerank、复杂 Query Rewrite；
- 多模型并行比较；
- 微服务；
- 分布式事务；
- 完整监控平台或 MQ 管理后台；
- Dashboard 和统计中心。

发现这些真实需求时只记录 Future Consideration 或新 ADR proposal，不在 V1 偷做。

## 5. 实施步骤

### Step A：跨阶段一致性清理

- 核对状态命名、错误模型和 owner filter；
- 核对 migration、索引、OpenAPI client；
- 清理重复实现和临时开发入口；
- 确认秘密不进入 Git、日志、历史消息或 Qdrant；
- 确认虚拟线程仍默认为 false；
- 确认无认证部署警告清晰可见。

### Step B：两个闭环的固定验收数据

- 设计少量专有、不会与通用模型知识混淆的样例；
- 固定 KnowledgeBase、笔记、会话和问题；
- 定义 route、filter、source 的可验证断言；
- 区分确定性系统断言与 LLM 质量观察；
- 将数据、步骤和期望写入 `eval/`。

### Step C：Playwright E2E

- 对话沉淀 happy path；
- Manual Note/上传笔记 happy path；
- 稳定等待 SSE、异步任务和索引状态；
- 使用可控模型 stub 完成默认 CI E2E；
- 必要时提供真实模型手工 E2E profile；
- 不依赖脆弱固定 sleep。

### Step D：失败演练与恢复

- 建立受控故障注入点；
- 逐项演练 FAILED、Retry、DLQ、重启恢复；
- 验证前端状态和日志 correlation；
- 修复无法定位或无法恢复的核心失败；
- 记录运行手册。

### Step E：本地验证与 GitHub Actions

- 完善 `verify-fast.sh`；
- 完善 `verify-integration.sh`；
- `verify-all.sh` 组合完整验证；
- GitHub Actions 只编排这些脚本；
- 对缓存、服务容器和测试报告做适度配置；
- 不在 YAML 中复制业务验证逻辑。

### Step F：最终文档和安全检查

- README、AGENTS、架构、领域、ADR、Eval、运行手册；
- `.gitignore` 和 tracked secret scan；
- 默认配置不含有效凭据；
- 仅 localhost/可信内网的安全边界；
- 记录公网部署前必须补充 Spring Security Session/HttpOnly Cookie/CSRF。

## 6. V1 Definition of Done

V1 只有同时满足以下条件才完成：

- 两个核心闭环可由用户通过正式 Vue 前端完成；
- 核心链路不是只靠 Postman 或测试代码可用；
- Chat History、Chat Memory、Knowledge Base 职责清楚且实现一致；
- 一个 Conversation 可逐轮切换 ChatModel，历史 Assistant Message 可追溯实际模型 Revision；
- Manual Note、Conversation Candidate、Markdown/TXT 都能形成统一 KnowledgeItem；
- 异步任务状态可见、可定位、幂等、有限 Retry、最终 FAILED/DLQ、可手动重试；
- Qdrant 始终按 userId 与 KnowledgeBase 隔离；
- 引用语义诚实且历史可追溯；
- 固定 Eval 验证 Router/Filter/Retrieval/Source；
- 本地统一验证入口通过；
- GitHub Actions 使用同一验证入口通过；
- 文档足以让新的开发者或 Codex 任务理解并验证核心链路；
- 所有 Phase 的独立 Review 阻断问题已关闭。

## 7. 最终验收记录

在 `docs/` 或 `eval/` 保存一次带版本的 V1 验收记录，至少包含：

- 被验收的 Git commit；
- 环境与依赖版本；
- 使用的模型配置标识，不含 API Key；
- 两个闭环的执行结果；
- 固定 Eval 结果；
- 失败演练结果；
- `verify-all.sh` 与 GitHub Actions 结果；
- 已知限制；
- 未解决但不阻断 V1 的 P2/P3 项。

## 8. 阶段交付物

- 可运行的 V1 前后端与基础设施编排；
- 两个核心闭环 Playwright E2E；
- 固定 Eval；
- 失败恢复演练；
- 本地完整验证脚本与 GitHub Actions；
- 完整的开发、架构、领域、运行和安全文档；
- V1 验收记录。

## 9. 完成后动作

1. 运行 `scripts/verify-all.sh`；
2. 运行受控真实模型端到端验收；
3. 确认 GitHub Actions 通过；
4. 提交候选 V1 版本；
5. 在全新 Codex 任务中依据 [REVIEW.md](./REVIEW.md) 做最终独立 Review；
6. 修复阻断项并重新 Review；
7. 由用户亲自走完两个闭环并解释核心调用链后，再标记 V1 完成。
