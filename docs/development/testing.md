# 开发与测试指南

## 环境准备

### 必需

- Java 21 (推荐 Eclipse Temurin)
- Node.js 22+
- npm 10+
- Docker + Docker Compose（集成测试与 E2E）

### 本地开发

KnowFlow 是一个 monorepo 项目。根 `pom.xml` 是 Maven 聚合父工程，后端和前端仍可独立开发：

```bash
# 后端（仓库根目录）
export KNOWFLOW_SECURITY_MASTER_KEY="$(openssl rand -base64 32)"       # 本地主密钥，不提交
./knowflow-app/mvnw -f pom.xml -pl knowflow-app spring-boot:run # 启动后端子模块
./knowflow-app/mvnw -f pom.xml compile                          # 编译父 Reactor
./knowflow-app/mvnw -f pom.xml test                             # 单元/结构测试
./knowflow-app/mvnw -f pom.xml verify                           # 含 *IT 集成测试（需要 Docker）
./knowflow-app/mvnw -f pom.xml spotless:check                   # 格式检查
./knowflow-app/mvnw -f pom.xml spotless:apply                   # 格式化

# 前端
npm --prefix frontend install  # 安装依赖
npm --prefix frontend run dev  # 启动开发服务器
npm --prefix frontend run build
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run test
npm --prefix frontend exec -- playwright install chromium # 首次 E2E 前
```

## 验证脚本

仓库级验证脚本位于 `scripts/`：

```bash
sh scripts/verify-fast.sh        # 快速验证（单元测试+typecheck+lint+format+build）
sh scripts/verify-integration.sh # MySQL Testcontainers 集成测试
sh scripts/verify-e2e.sh         # 隔离基础设施 + stub + Playwright
sh scripts/verify-failure-drills.sh # 定向 Retry/DLQ/恢复/SSE 演练
sh scripts/check-tracked-secrets.sh # tracked/待跟踪文件高置信 Secret 扫描
sh scripts/verify-all.sh         # 默认完整确定性验证
sh scripts/check-api-contract.sh # 后端运行时检查 OpenAPI 快照漂移
sh scripts/live-smoke.sh DeepSeek <config-id> # 显式真实 Provider 验证
```

- `verify-fast.sh` 不要求 Docker，运行后端单元/结构测试和完整前端静态验证。
- `verify-integration.sh` 需要 Docker，运行以 `IT` 结尾的 Spring 集成测试（MySQL、Redis、
  RabbitMQ、Qdrant 按测试需要使用 Testcontainers）。
- `verify-e2e.sh` 每次创建专用 Compose project/数据库/队列/临时文件目录，Playwright 管理应用、
  Vite 与本地模型 stub；不复用开发服务器。
- `verify-all.sh` 依次运行 fast、Secret scan、integration 和 E2E；这是本地与 GitHub Actions
  的默认完整确定性事实源。
- `check-api-contract.sh` 需要本地后端已经启动；它比较 `/v3/api-docs` 与
  `docs/api/openapi.json`。集成测试也会校验运行时契约快照。

## 测试原则

- 单元测试应覆盖业务规则、事务、隔离和边界条件。
- 默认不调用真实云端模型。
- 集成测试使用 Testcontainers（MySQL、Redis、RabbitMQ、Qdrant）。
- 数据库集成测试不使用 H2。
- E2E 测试使用 Playwright，验证真实前端闭环。
- 真实云端 Provider smoke/eval 必须显式执行，不能读取 CI 默认 Secret，也不能把模型波动作为
  每个 PR 的硬门禁。

## 代码风格

- 后端：Spotless + google-java-format
- 前端：ESLint + Prettier

## 配置文件

- `.env.example` 只包含当前 Phase 实际使用的环境变量模板。
- 复制为 `.env` 并填入本地值，`.env` 已被 Git 忽略。
- 所有 Secret（API Key、密码）必须通过环境变量注入，禁止硬编码。
