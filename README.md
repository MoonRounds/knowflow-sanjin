# KnowFlow

个人 AI 学习与知识沉淀系统。KnowFlow 帮助个人用户在与 AI 的对话中提取、整理、索引和检索知识，形成个人知识闭环。

## 当前阶段

**Phase 3 — Conversation、Message 与基础 Chat（开发中）**

Phase 0 的前后端骨架、Phase 1 的 Owner/KnowledgeBase 全栈切片以及 Phase 2 的 ModelConfig
Review 修复已经建立。Phase 3 代码正在开发；DeepSeek 与 Qwen 的 Phase 2 真实 Live Smoke 仍需
Owner 使用本地 Secret 显式执行，因此 Provider 兼容性验收仍标记为待完成。

## 目录结构

```text
knowflow-sanjin/
├── pom.xml         # Maven 聚合父工程与统一版本管理
├── knowflow-app/   # Spring Boot 后端
├── frontend/       # Vue 3 + TypeScript 前端
├── docs/           # 产品、架构、领域文档与开发计划
├── eval/           # AI/RAG 评估数据
└── scripts/        # 仓库级验证脚本
```

后端 Java 包采用 `common + modules` 的模块化结构，启动类独占根包；业务模块内部明确区分
Controller、Service、Mapper、Entity、DTO、VO、Assembler 和 Exception。完整约定见
[后端包结构](docs/architecture/backend-package-structure.md)。

## 快速开始

### 环境要求

- Java 21
- Node.js 22+
- npm 10+
- Docker Desktop 或其他兼容 Docker 环境

### 启动 MySQL

```bash
# 可选：复制占位配置后再按本机需要修改
cp .env.example .env
docker compose up -d mysql
```

Docker Compose 会读取根目录 `.env`。如果修改了数据库变量，启动 Spring Boot 前还需要把同一组
变量导入当前终端；未修改时，应用和 Compose 的无效占位默认值已经保持一致。

### 启动后端

```bash
# 在当前 shell 生成并导出本地主密钥；不要写入仓库或命令历史共享记录
export KNOWFLOW_SECURITY_MASTER_KEY="$(openssl rand -base64 32)"

./knowflow-app/mvnw -f pom.xml -pl knowflow-app spring-boot:run
```

主密钥必须稳定保存于本机 Secret 管理方式中。更换或丢失该密钥后，已有 ModelConfig API Key
无法解密；`.env` 只会被 Docker Compose 自动读取，Spring Boot 不会自动导入根 `.env`。

后端启动后访问以下任一地址确认健康状态：

- http://localhost:8080/actuator/health（Actuator）
- http://localhost:8080/api/v1/health（前端连通检查）

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认运行在 http://localhost:5173，代理到后端 8080 端口。

### 运行验证

```bash
# 快速验证：编译、单元测试、typecheck、lint、format、build
sh scripts/verify-fast.sh

# MySQL 8.4 Testcontainers 集成测试
sh scripts/verify-integration.sh

# 完整验证
sh scripts/verify-all.sh

# 后端运行时检查 OpenAPI 快照是否漂移
sh scripts/check-api-contract.sh

# 显式真实 Provider 验证；分别传入已配置的 DeepSeek/Qwen ModelConfig ID
sh scripts/live-smoke.sh DeepSeek <deepseek-config-id>
sh scripts/live-smoke.sh Qwen <qwen-config-id>
```

## 文档

- [KnowFlow V1 开发路线图](docs/plans/README.md)
- [V1 决策基线](docs/plans/DECISIONS.md)
- [执行与 Review 工作流](docs/plans/WORKFLOW.md)
- [产品范围](docs/product/v1-scope.md)
- [系统上下文](docs/architecture/system-context.md)
- [后端包结构](docs/architecture/backend-package-structure.md)
- [Maven 父子工程决策](docs/adr/0001-maven-parent-reactor.md)
- [开发与测试指南](docs/development/testing.md)

## 安全说明

- V1 为单用户本地系统，不实现登录认证。
- 数据库凭据与应用主密钥通过环境变量注入；Provider API Key 通过 Model Settings 保存为认证密文。
- `.env` 已被 Git 忽略，且不会被 Spring Boot 自动读取。
- 禁止将密钥、证书或真实配置提交到仓库。
