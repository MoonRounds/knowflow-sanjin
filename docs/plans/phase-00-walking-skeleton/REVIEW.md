# Phase 0 REVIEW：仓库安全与 Walking Skeleton

## Review 目标

只读审查 Phase 0 是否建立了安全、可重复、没有过早依赖的工程骨架。默认不修改文件。

## 必须输入

- Phase 起始 commit 或空仓库基线说明。
- Phase 完成 commit。
- `AGENTS.md`。
- `docs/plans/DECISIONS.md`。
- 本 Phase `PLAN.md`。

## 核心检查

### Repository Safety

- `.env`、API Key、数据库密码、私钥、日志和构建产物是否被可靠忽略。
- `.env.example` 是否只有非敏感示例。
- 首次提交是否包含意外的 IDE 或系统文件。
- README、AGENTS 和脚本中的命令是否真实可运行。

### Backend Baseline

- Maven 坐标、Java 版本、Spring Boot 正式版本和根包是否正确。
- 是否误用了 WebFlux、Lombok、MapStruct、数据库或 AI 依赖。
- Virtual Threads 是否显式关闭。
- Actuator 暴露面是否仅限 health/info。
- Maven Wrapper 是否完整且可执行。

### Frontend Baseline

- 是否确实使用 Vue 3、TypeScript、Vite、Router、Element Plus 和 npm lockfile。
- health URL 是否通过配置/代理管理，而不是散落硬编码。
- 是否提前创建了无用的全局 Store 或未来业务空壳。
- Markdown、认证或业务页面是否被误实现。

### Harness

- `verify-fast.sh` 是否从任意合理工作目录安全定位仓库根。
- 每个失败是否产生非零退出码。
- GitHub Actions 是否只调用仓库脚本。
- CI 和本地 Node/Java 版本是否一致。

## 必跑验证

- 执行 `scripts/verify-fast.sh`。
- 独立执行 backend 和 frontend build/test，确认脚本没有掩盖失败。
- 检查 Git tracked/ignored 文件。
- 启动前后端并观察 `/chat` 到 health 的连通性。

## 不应报告为缺陷

- 没有 MySQL、业务表、Chat、RAG 或完整 UI。
- 没有 Docker Compose。
- 没有 integration/E2E test。

## Review 输出

先列 P0–P3 Findings；无阻塞问题时明确写“Phase 0 未发现阻塞性问题”，再列验证结果和 Phase 1 前仍存在的预期空白。

