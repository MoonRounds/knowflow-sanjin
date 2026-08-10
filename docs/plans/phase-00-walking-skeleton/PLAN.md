# Phase 0 PLAN：仓库安全与前后端 Walking Skeleton

## 目标

建立不会被后续丢弃的仓库、后端、前端和验证骨架，证明全新 clone 可以按文档运行，并让本地验证与 GitHub Actions 使用同一入口。

本阶段不实现任何业务功能，不连接数据库、模型或中间件。

## 开始前必须阅读

- `docs/plans/DECISIONS.md`
- `docs/plans/WORKFLOW.md`
- 本文件

## 前置条件

- 当前分支为 `main`。
- GitHub `origin` 已配置。
- 记录本阶段起始 commit；若仓库仍无 commit，则在交付中明确说明。
- 工作树中的既有修改必须先识别和保护。

## In Scope

- 根目录安全文件、基础说明和 Harness 文档。
- `knowflow-app/` Spring Boot Walking Skeleton。
- `frontend/` Vue Walking Skeleton。
- 根级快速验证脚本。
- 最小 GitHub Actions。
- 前端到后端 health 的真实连通验证。

## Out of Scope

- MySQL、Flyway、MyBatis-Plus。
- Spring AI、ModelConfig、真实模型调用。
- Redis、RabbitMQ、Qdrant、Docker Compose 基础设施。
- Conversation、KnowledgeBase 或其他业务表/API。
- 登录与认证。

## 交付物

### Repository

- `.gitignore`：至少忽略 `.env`、Secret、本地配置覆盖、IDE 文件、日志、`target/`、`node_modules/`、`dist/`、测试与运行产物。
- `.env.example`：只列变量名、非敏感示例和说明。
- `.editorconfig`。
- 根 `README.md`：项目定位、目录、当前阶段、启动和验证入口。
- 核对并按实际 Walking Skeleton 更新根 `AGENTS.md`：范围控制、文档入口、验证命令、Secret 规则和阶段工作流。
- `docs/product/v1-scope.md`。
- `docs/architecture/system-context.md`。
- `docs/development/testing.md`。

### Backend

- `knowflow-app/pom.xml`。
- Maven 坐标 `knowflow.sanjin:knowflow-app`。
- Java 21、Spring Boot 4.1.x 正式版、Spring MVC、Validation、Actuator、测试依赖。
- Maven Wrapper。
- 根包 `knowflow.sanjin`。
- `spring.threads.virtual.enabled=false`。
- Actuator 只暴露 health/info，响应不包含敏感细节。
- Spotless + google-java-format。
- 一个最小 ApplicationContext/health 测试。

### Frontend

- Vue 3 + TypeScript + Vite + Vue Router + Element Plus。
- npm 与 `package-lock.json`。
- ESLint、Prettier、Vitest。
- `/` 重定向到 `/chat`。
- `/chat` 只展示应用骨架和后端 health 连通状态，不伪造聊天功能。
- 开发代理正确访问后端，地址不硬编码在组件内。
- 一个最小组件或状态测试。

### Harness

- `scripts/verify-fast.sh`：调用 `./knowflow-app/mvnw` 与 `npm --prefix frontend ...`。
- 失败立即返回非零退出码。
- 输出清晰阶段名称。
- GitHub Actions 在 push/PR 上调用同一脚本。
- CI 不包含 Secret，也不使用 `latest` action 的不固定引用策略。

## 执行 Checkpoints

### Checkpoint A：Repository Safety

1. 完善 `.gitignore` 与 `.env.example`。
2. 扫描当前待提交文件，确认没有 Secret、构建产物或 IDE 临时文件。
3. 创建最小文档入口，不复制 `DECISIONS.md` 全文。

### Checkpoint B：Backend Skeleton

1. 生成/创建 Spring Boot 工程。
2. 只加入本阶段依赖。
3. 配置 Java、Maven Wrapper、Actuator 和格式化。
4. 运行 compile/test/format check。

### Checkpoint C：Frontend Skeleton

1. 使用正式 Vue/Vite TypeScript 工程结构。
2. 配置 Router、Element Plus、lint、format、typecheck 和 Vitest。
3. 实现后端 health 连接状态。
4. 不创建未来业务页面的空 Service/Store。

### Checkpoint D：Unified Verification and CI

1. 建立 `verify-fast.sh`。
2. 本地从仓库根目录运行。
3. 建立最小 GitHub Actions 并只调用该脚本。
4. 更新 README 中的相同命令。

## Required Verification

- Backend compile。
- Backend unit/context tests。
- Backend Spotless check。
- Frontend typecheck。
- Frontend unit test。
- Frontend lint/format check。
- Frontend production build。
- `scripts/verify-fast.sh` 从根目录成功。
- 手动启动前后端，确认 `/chat` 能显示后端 health 状态。
- `git status --ignored` 抽查 Secret 和生成目录确实被忽略。

## 验收标准

- 全新 clone 只依赖 README 即可运行。
- `knowflow-app/` 与 `frontend/` 可独立构建。
- 本地与 CI 共用相同验证逻辑。
- 仓库中不存在真实 Secret。
- 没有任何数据库、AI、中间件或业务功能的提前实现。

## 停止条件

- Spring Boot 4.1、Java 21 或前端依赖出现正式兼容性冲突。
- 需要加入计划外框架才能完成基础骨架。
- 发现工作树中存在不属于本阶段且无法安全绕过的用户修改。

遇到停止条件时只报告证据和选择，不静默更换技术栈。

## Phase Handoff

最终交付必须说明：

- 目录与验证入口；
- 本地启动顺序；
- 前端如何访问后端；
- CI 如何复用本地脚本；
- Secret 防护；
- 本阶段明确未实现的功能。
