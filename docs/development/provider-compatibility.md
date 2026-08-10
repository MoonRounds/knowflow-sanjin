# Provider Compatibility 记录

本文件记录 V1 通过同一 OpenAI-Compatible 基础契约实测过的云端 Provider。所有 Provider
共用一套 ChatModel Factory/Registry，代码中不存在厂商分支。未实测能力一律标注"可能兼容"。

## 实测基线

- 日期：Phase 2（模型配置）执行期间。
- 契约：OpenAI `/v1/chat/completions`，同步 + 流式。
- 基础参数：Base URL、Model Name、temperature、maxOutputTokens。
- 验证方式：本地 OpenAI-Compatible Stub 契约测试（默认 CI）+ 显式 Live Smoke（仅本地 Secret 存在时）。

## 已验证 Provider

> Live Smoke 需要真实 API Key 与本地 `KNOWFLOW_SECURITY_MASTER_KEY`。当前开发环境
> 未配置真实 Secret，Live Smoke 未运行。下表为计划记录模板；实测后在此补充日期与结果。

### DeepSeek（计划实测）

| 项目 | 值 |
|---|---|
| 记录日期 | （待实测） |
| Base URL | `https://api.deepseek.com` |
| Model | `deepseek-chat` |
| 同步文本 | ✅ / ❌（待实测） |
| 流式 | ✅ / ❌（待实测） |
| Usage | ✅ / ❌（待实测） |
| 结构化输出（Router/Candidate） | ✅ / ❌（待实测） |
| 行为差异 | （记录，如 temperature 边界、max_tokens 限制） |
| 不承诺能力 | Tool Calling、Vision、reasoning content 展示 |

### Qwen（计划实测）

| 项目 | 值 |
|---|---|
| 记录日期 | （待实测） |
| Base URL | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| Model | `qwen-plus` 或 `qwen-max` |
| 同步文本 | ✅ / ❌（待实测） |
| 流式 | ✅ / ❌（待实测） |
| Usage | ✅ / ❌（待实测） |
| 结构化输出（Router/Candidate） | ✅ / ❌（待实测） |
| 行为差异 | （记录） |
| 不承诺能力 | Tool Calling、Vision、reasoning content 展示 |

## 运行方式

```bash
# 1. 后端使用本地主密钥启动
# 2. 通过前端 /model-settings 或 API 创建 DeepSeek/Qwen ModelConfig（填入真实 Key）
# 3. 显式、逐 Provider 执行（需要后端已持有本地 Secret，不加入默认 CI）
sh scripts/live-smoke.sh DeepSeek <deepseek-config-id>
sh scripts/live-smoke.sh Qwen <qwen-config-id>
```

脚本会分别验证同步文本、流式文本、Usage 可用性记录，以及 Router/Candidate 两类结构化输出。
任一强制能力失败都会返回非零退出码；Usage 按 V1 决策只做尽力记录。

## 未承诺能力（V1）

- Ollama、本地模型、私有 Provider SDK。
- Tool Calling、Function Calling、Vision。
- 推理过程（reasoning content）展示。
- 任意 Provider 参数 JSON、后台健康巡检、费用 Dashboard。
