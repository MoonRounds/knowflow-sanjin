# Phase 6 Eval 基线结果

> 记录确定性测试与可选真实模型 smoke 的结果。真实 smoke 需显式配置模型/Embedding 且不在 CI 运行。

## 确定性链路测试（stub 驱动）

运行方式：`./knowflow-app/mvnw -s knowflow-app/.mvn/settings.xml -f pom.xml -pl knowflow-app test -Dtest=RouterServiceTest,RetrievalServiceTest,RagContextBuilderTest,CitationParserTest,RagVerticalSliceIT`

- RouterServiceTest：12 通过（含 enabled KB 无可检索 Item 的空目录守卫）
- RetrievalServiceTest：10 通过（含 owner + 多 KB OR filter 显式断言）
- RagContextBuilderTest：9 通过（含 Router 抛异常 → DEGRADED 不泄漏、检索失败保留诊断 trace）
- CitationParserTest：4 通过
- RagVerticalSliceIT：3 通过（真实 MySQL+RabbitMQ+Qdrant+Redis + stub Embedding，含笔记 prompt-injection 隔离）

上述全部纳入 `scripts/verify-fast.sh` 与 `scripts/verify-integration.sh`，两者均通过。
OpenApiContractIT（运行时契约 vs 快照）与前端类型一致性检查同样通过。

覆盖：needRag=false 零检索调用、0/1/多 KB、非法 ID 拒绝、单次修复、DEGRADED、owner filter、
KB OR filter、过期版本剔除、Item 删除/关系移除剔除、低分剔除、retrieved vs cited、[S99] 不伪造、trace 落库。

## 真实模型 smoke

未运行（需真实 Utility/Embedding Key 与已索引 Manual Note）。普通 PR/CI 不读取真实 Secret。
待显式运行后在此记录基线。
