# KnowFlow V1 验收记录

> 复制本模板为带日期或版本的记录。不得写入 API Key、主密钥、完整私人聊天、知识正文或完整 Prompt。

## 版本与环境

- 验收日期：`YYYY-MM-DD`
- 被验收 commit：`<git commit>`
- 工作树状态：`clean | dirty（列出原因）`
- 操作系统 / CPU：`<value>`
- Java / Node.js / npm：`<versions>`
- Docker / Compose：`<versions>`
- MySQL / Redis / RabbitMQ / Qdrant：`<versions>`
- 浏览器 / Playwright：`<versions>`

## 模型标识（不含 Secret）

- ChatModel Config ID / Provider / Model：`<value>`
- Utility Model Config ID / Provider / Model：`<value>`
- Embedding Provider / Model / dimensions：`<value>`
- 确定性 Stub 版本：`<commit>`

## 自动化验收

| 命令 | 结果 | 时间 | 备注 |
|---|---|---|---|
| `sh scripts/verify-fast.sh` | `PASS/FAIL/NOT_RUN` | | |
| `sh scripts/verify-integration.sh` | `PASS/FAIL/NOT_RUN` | | |
| `sh scripts/verify-e2e.sh` | `PASS/FAIL/NOT_RUN` | | |
| `sh scripts/verify-failure-drills.sh` | `PASS/FAIL/NOT_RUN` | | |
| `sh scripts/verify-all.sh` | `PASS/FAIL/NOT_RUN` | | |

断言矩阵结果：

| 范围 | 结果 | 失败 ID / 证据 |
|---|---|---|
| C-01..C-06 | `PASS/FAIL/NOT_RUN` | |
| N-01..N-06 | `PASS/FAIL/NOT_RUN` | |
| X-01..X-04 | `PASS/FAIL/NOT_RUN` | |
| F-01..F-04 | `PASS/FAIL/NOT_RUN` | |

## 两个正式前端闭环

### 对话沉淀闭环

- 结果：`PASS/FAIL/NOT_RUN`
- Conversation / Candidate / Item 标识：`<non-secret ids>`
- Router / RAG / Sources：`<summary>`
- 证据：`<test report or observation>`

### Manual Note / Upload 闭环

- 结果：`PASS/FAIL/NOT_RUN`
- KnowledgeBase / Manual Item / Upload Item 标识：`<non-secret ids>`
- Document Parse / Index / Sources：`<summary>`
- 证据：`<test report or observation>`

## 失败与恢复演练

| 场景 | MySQL 终态 | Retry / DLQ | 前端可见性 | 结果 |
|---|---|---|---|---|
| Extraction 失败 | | | | `PASS/FAIL/NOT_RUN` |
| Document Processing 失败 | | | | `PASS/FAIL/NOT_RUN` |
| Embedding 失败 | | | | `PASS/FAIL/NOT_RUN` |
| Qdrant Index 失败 | | | | `PASS/FAIL/NOT_RUN` |
| Consumer 最大重试 | | | | `PASS/FAIL/NOT_RUN` |
| PENDING / PROCESSING 重启恢复 | | | | `PASS/FAIL/NOT_RUN` |
| 用户手动 Retry | | | | `PASS/FAIL/NOT_RUN` |
| SSE 中断 / 停止 / 重新生成 | | | | `PASS/FAIL/NOT_RUN` |

## 真实模型受控验收

- 执行条件：仅在环境已配置凭据且 Owner 允许费用时运行。
- 结果：`PASS/FAIL/NOT_RUN`
- 未运行原因：`<no credentials / cost gate / other>`
- DeepSeek / Qwen 兼容性观察：`<summary>`
- Router / Retrieval / Citation 质量观察：`<summary>`

## GitHub Actions

- Workflow / Run URL：`<url or NOT_RUN>`
- 被验证 commit：`<commit>`
- 结果：`PASS/FAIL/NOT_RUN`
- 未运行原因：`<reason>`

## 已知限制与非阻断项

- P2/P3：`<list>`
- 环境限制：`<list>`
- V1 明确排除项：认证、公网部署、PDF/Word、MinIO、MCP、Agent、GraphRAG、Hybrid Search、Dashboard。

## 结论

- V1 候选结论：`ACCEPTED / REJECTED / PARTIAL`
- 阻断项：`<none or list>`
- Owner 验收：`<name/date or pending>`
