# 开发与测试指南

## 环境准备

### 必需

- Java 21 (推荐 Eclipse Temurin)
- Node.js 22+
- npm 10+

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
```

## 验证脚本

仓库级验证脚本位于 `scripts/`：

```bash
sh scripts/verify-fast.sh        # 快速验证（单元测试+typecheck+lint+format+build）
sh scripts/verify-integration.sh # MySQL Testcontainers 集成测试
sh scripts/verify-all.sh         # 快速验证与集成验证
sh scripts/check-api-contract.sh # 后端运行时检查 OpenAPI 快照漂移
sh scripts/live-smoke.sh DeepSeek <config-id> # 显式真实 Provider 验证
```

- `verify-fast.sh` 不要求 Docker，运行后端单元/结构测试和完整前端静态验证。
- `verify-integration.sh` 需要 Docker，运行以 `IT` 结尾的 Spring/MySQL 集成测试。
- `verify-all.sh` 依次调用以上两个事实源入口，并检查 OpenAPI 生成类型无漂移。
- `check-api-contract.sh` 需要本地后端已经启动；它比较 `/v3/api-docs` 与
  `docs/api/openapi.json`。集成测试也会校验运行时契约快照。

## 测试原则

- 单元测试应覆盖业务规则、事务、隔离和边界条件。
- 默认不调用真实云端模型。
- 集成测试使用 Testcontainers（MySQL、Redis、RabbitMQ、Qdrant）。
- 数据库集成测试不使用 H2。
- E2E 测试使用 Playwright，验证真实前端闭环。

## 代码风格

- 后端：Spotless + google-java-format
- 前端：ESLint + Prettier

## 配置文件

- `.env.example` 只包含当前 Phase 实际使用的环境变量模板。
- 复制为 `.env` 并填入本地值，`.env` 已被 Git 忽略。
- 所有 Secret（API Key、密码）必须通过环境变量注入，禁止硬编码。
