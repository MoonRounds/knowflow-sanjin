# Phase 09 确定性断言矩阵

本矩阵把固定样例映射到默认 CI 可执行证据。状态只由实际命令结果填写；文档中的预期不等于通过。
真实模型回答质量属于观察项，不替代确定性断言。

## 对话沉淀闭环

| ID | 确定性断言 | 默认证据 |
|---|---|---|
| C-01 | 通过正式设置页创建模型、连接测试、Utility 能力测试，并设置默认 Chat/Utility | `frontend/e2e/conversation-loop.spec.ts` |
| C-02 | 两轮 SSE 完成；第二轮问题依赖前一轮上下文；会话锁定实际 Model Revision | Playwright + Conversation/Memory 后端测试 |
| C-03 | Extraction Task `SUCCEEDED`，cutoff 覆盖两轮完整 active turns，只产生 1 个 Candidate | Playwright + `ExtractionTaskConsumerIT` |
| C-04 | 用户在正式 Candidates 页查看、编辑并确认；重复 confirm 不创建第二个 Item | Playwright + Candidate 幂等集成测试 |
| C-05 | Item 最终 `INDEXED`，新会话 C-Q1 为 `ragStatus=USED`，来源指向确认后的 Item 且 cited | Playwright + `RagVerticalSliceIT` |
| C-06 | C-Q2 为 `ragStatus=NOT_NEEDED` 且没有来源 | Playwright + Router 单元/集成测试 |

## Manual Note / Upload 闭环

| ID | 确定性断言 | 默认证据 |
|---|---|---|
| N-01 | 通过正式 Knowledge 页创建 KnowledgeBase 和 Manual Note | `frontend/e2e/note-loop.spec.ts` |
| N-02 | Manual Note 最终 `INDEXED`；N-Q1 为 `ragStatus=USED`，来源指向该 Item | Playwright + Index/RAG 集成测试 |
| N-03 | 通过正式 Upload 入口上传 Markdown；Document Parsing 与 Index Task 都 `SUCCEEDED` | Playwright + `DocumentParseConsumerIT` |
| N-04 | Item Detail 展示 `UPLOAD_FILE`、原文件名、解析状态与下载入口 | Playwright |
| N-05 | 相同字节、不同文件名的重复上传复用同一 Item | Document Upload 集成测试 |
| N-06 | N-Q2 为 `ragStatus=USED`，来源指向上传 Item，知识库过滤没有串库 | Playwright + `RagVerticalSliceIT` |

## 跨闭环不变量

| ID | 确定性断言 | 默认证据 |
|---|---|---|
| X-01 | API/SSE/OpenAPI 中 BIGINT ID 为字符串 | OpenAPI snapshot、生成类型 drift、Controller 测试 |
| X-02 | MySQL、Redis key、Rabbit task、Qdrant payload/filter 保留 owner 边界 | 后端单元/集成测试 |
| X-03 | E2E 仅连接 `e2e` profile 和专用 Compose 项目；清理前有 guard | `scripts/verify-e2e.sh` + Playwright helper |
| X-04 | Stub 数据、验收记录和日志不含真实 Secret、私人正文或完整 Prompt | tracked secret scan + 人工复核 |

## 失败与恢复

| ID | 确定性断言 | 默认证据 |
|---|---|---|
| F-01 | Extraction/Document/Embedding/Qdrant 故障映射稳定错误码；终态或最大重试后 MySQL FAILED 与对应 DLQ task ID 一致 | Consumer IT + QdrantClientTest |
| F-02 | 滞留 PENDING 与租约超时 PROCESSING 可恢复；lease 内任务不误发 | `ProcessingTaskServiceRecoveryIT` |
| F-03 | 手动 Retry 创建 `retryOfTaskId` 关联的新任务，重置 Item/File/Extraction snapshot 状态并成功重发 | Recovery/Extraction IT + Playwright failure path |
| F-04 | SSE 断连落 FAILED 并释放 slot；停止落 CANCELLED；regenerate 创建新 active attempt | GenerationStreamerTest + Playwright failure path |

## 质量观察项（不作为默认 CI 硬阻断）

- 真实 ChatModel 对 C-Q1、N-Q1、N-Q2 的答案是否正确引用本次检索来源。
- 真实 Utility Model 是否选择预期 KnowledgeBase，且 C-Q2 不触发检索。
- Provider 流式兼容性、Token Usage 和延迟只记录观察结果，不承诺跨 Provider 完全一致。
