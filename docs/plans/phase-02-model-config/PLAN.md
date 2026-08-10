# Phase 2 PLAN：ModelConfig、Revision 与 Provider Compatibility

## 目标

建立安全、可追溯、可动态选择的云端 OpenAI-Compatible 模型配置能力，并验证至少两个真实 Provider，而不实现聊天业务。

## 前置条件

- Phase 1 Review 已通过。
- MySQL、Flyway、Owner 边界和 OpenAPI 已稳定。
- 执行前确认本地具备应用主加密密钥；真实 Provider Key 不进入 Git。

## In Scope

- `model_config`、`model_config_revision`、`owner_ai_settings`。
- API Key 加密、掩码、Revision、enabled/soft delete。
- ModelConfig CRUD、Test Connection、Utility Capability Test。
- Default Chat Model 与 Utility Model 设置。
- 简单 ChatModel Factory/Registry 边界。
- 本地 OpenAI-Compatible Stub。
- 至少 DeepSeek + Qwen 两家真实兼容性验证文档。
- Model Settings 前端页面。

## Out of Scope

- Conversation、Message、SSE。
- Router 和 Extraction 的真实业务调用。
- Ollama、本地模型、私有 Provider SDK。
- Tool Calling、Vision、reasoning content 展示。
- 任意 Provider 参数 JSON、后台健康巡检、费用 Dashboard。

## 核心规则

- ModelConfig 是逻辑配置，Revision 不可变。
- 编辑创建新 Revision，并切换 current revision。
- Revision 保存 provider type、display/provider name、Base URL、Model Name、temperature、maxOutputTokens、加密 API Key 和 encryption version。
- API Key 使用标准认证加密与随机 nonce，主密钥只来自环境 Secret。
- 查询 API 只返回掩码，不返回明文或密文。
- 保存与测试连接分离。
- enabled 配置才能被新调用选择。
- Utility 设置前必须通过结构化输出能力测试。
- Base URL 默认只允许安全 HTTPS 云端地址，防止 SSRF。
- 普通 Chat 兼容测试与 Utility 结构化能力测试分开记录。

## 执行 Checkpoints

### Checkpoint A：Schema and Encryption Boundary

1. Migration 建立三张表、外键、唯一约束和 Revision 关系。
2. 实现 Secret encryption service，覆盖随机 nonce、错误主密钥、密文版本和日志脱敏测试。
3. 缺少主密钥时采用明确启动/功能失败行为，不生成并提交临时密钥。

### Checkpoint B：ModelConfig Use Cases

1. CRUD、Revision 更新、enable/disable、soft delete。
2. Owner 默认 Chat/Utility 设置。
3. 被引用 Revision 长期保留。
4. 明确 409/validation/error codes。

### Checkpoint C：Provider Client Boundary

1. 引入 Spring AI OpenAI-Compatible 正式依赖。
2. 实现按具体 Revision 创建/获取客户端的小边界。
3. 不在业务代码散落 Provider if/else。
4. 实现连接/读取/总体超时、并发限制基础和脱敏错误映射。

### Checkpoint D：Compatibility Tests

1. Stub 覆盖普通响应、流式协议形态、JSON、usage、401、429、timeout 和非法 JSON。
2. Utility Capability Test 校验 Router 与 Candidate 两类 Schema。
3. 显式 Live Smoke 验证 DeepSeek + Qwen，不加入默认 CI。
4. 记录已验证模型、日期、行为差异和不承诺能力。

### Checkpoint E：Frontend

1. `/model-settings` 管理配置、Revision 更新、掩码、启用/禁用。
2. Test Connection 与 Utility Capability Test 分开展示。
3. 设置 defaultChatModel 和 utilityModel。
4. 浏览器状态和错误中不得出现 Secret。

## Required Verification

- 加密 round-trip、不同 nonce、错误 key、Secret redaction tests。
- Revision 不可变和 current 切换 tests。
- 被禁用/删除配置不能用于新选择。
- Base URL SSRF/validation tests。
- Stub compatibility contract tests。
- Model Settings 前端测试。
- OpenAPI 类型检查。
- Live Smoke 只在显式命令和本地 Secret 存在时运行。
- 全仓 Secret scan。

## 验收标准

- 前端可安全创建、修改、测试和选择 ModelConfig。
- 历史 Revision 不被覆盖。
- Utility Model 不能绕过能力测试。
- 至少两家 Provider 有实测记录，但代码没有厂商分支。
- 尚未创建 Conversation 或聊天 UI。

## 停止条件

- Spring AI 2.0 无法按 Revision 动态配置 Base URL/Key/Model。
- 两家 Provider 的流式或结构化行为无法由同一基础契约承载。
- SSRF 防护需要改变已确认的云端 Base URL 产品边界。

停止时先提交 Compatibility Spike 证据，再决定是否增加基础设施层 Adapter。

