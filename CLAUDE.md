# KnowFlow Repository Instructions

本文件是所有在本仓库中工作的 AI Agent 和开发者必须遵守的仓库级协作规则。

## 1. 开始工作前

每个任务开始时必须依次：

1. 检查 `git status --short`，保护用户已有修改和未跟踪文件；
2. 阅读 `docs/plans/DECISIONS.md`；
3. 阅读当前 Phase 的 `PLAN.md`；
4. 如果任务是 Review，再完整阅读当前 Phase 的 `REVIEW.md`；
5. 记录任务起始 commit；仓库尚无 commit 时明确记录为空仓库基线。

规划总入口：`docs/plans/README.md`。

执行与独立 Review 工作流：`docs/plans/WORKFLOW.md`。

## 2. 决策优先级

出现冲突时按以下顺序处理：

1. 用户在当前任务中的明确指令；
2. 本文件；
3. `docs/plans/DECISIONS.md`；
4. 当前 Phase 的 `PLAN.md` 或 `REVIEW.md`；
5. 其他架构、领域、ADR 和开发文档；
6. 现有代码惯例。

如果实现需要改变已确认的跨 Phase 决策，停止编码，说明原因、替代方案、复杂度和收益，等待项目 Owner 确认。不得静默修改技术栈或产品边界。

## 3. Phase 范围控制

- 默认按 Phase 0 → 9 顺序执行。
- 只实现当前 Phase 的 In Scope。
- 不把后续 Phase 的功能、依赖、表、接口或抽象提前加入当前实现。
- 不为未来需求创建空 Service、空 Adapter、通用 Base 类或未使用扩展点。
- 一个 Phase 的阻断 Review Finding 未关闭前，不进入下一 Phase。
- Review 任务默认只读；除非用户明确要求修复，否则不修改文件。
- Review 不得把当前 Phase 明确排除的后续能力作为缺陷。

## 4. 仓库结构

统一使用以下目录，不创建 `backend/`、`knowflow-server/` 或 `knowflow-web/` 等替代目录：

```text
knowflow-sanjin/
├── pom.xml         # Maven parent and reactor
├── knowflow-app/   # Spring Boot backend
├── frontend/       # Vue 3 + TypeScript frontend
├── docs/           # product, architecture, domain, ADR and plans
├── eval/           # AI/RAG evaluation data and instructions
└── scripts/        # repository-level verification entry points
```

根 Maven 坐标为 `knowflow.sanjin:knowflow-sanjin`，`packaging=pom`；`knowflow-app` 必须作为其
子模块并继承根 POM。Java 根包统一为 `knowflow.sanjin`。不要使用
`io.github.moonrounds.knowflow`。

后端业务代码使用 `knowflow.sanjin.modules.<feature>...`，跨模块技术能力使用
`knowflow.sanjin.common...`；具体职责分层见
`docs/architecture/backend-package-structure.md`。

## 5. 验证入口

仓库级 POSIX Shell 脚本是本地和 CI 的验证事实源。脚本在 Phase 0 建立后，优先使用：

- `scripts/verify-fast.sh`：快速编译、单元测试、前端 typecheck/test/build 和基础格式检查；
- `scripts/verify-integration.sh`：需要容器或真实基础设施语义的集成测试；
- `scripts/verify-all.sh`：完整验证组合。

脚本内部应使用：

- 后端：`./knowflow-app/mvnw ...`；
- 前端：`npm --prefix frontend ...`。

不要依赖全局 Maven。GitHub Actions 只能调用仓库已有验证脚本，不在 Workflow YAML 中复制一套核心验证逻辑。

执行任务结束前必须报告实际运行的命令、结果和未运行原因。不得声称未实际执行的验证已经通过。

## 6. 工程原则

- 采用模块化单体，不拆微服务。
- 优先清晰、可解释、可维护和可测试的直接实现。
- 不为每个 Service 强制创建 Interface。
- 不套用完全对称的 DDD/六边形目录模板；模块复杂后再按真实需要分层。
- API Entity 不直接作为 DTO；使用显式映射，不引入 MapStruct。
- 不使用 Lombok、Resilience4j 或未确认的新框架。
- 不事件化所有内部调用；只有已确认的异步边界使用 ProcessingTask + RabbitMQ。
- MySQL 是业务事实源；Redis Memory 和 Qdrant Vector Index 必须可从事实数据重建。
- 业务逻辑不得依赖 RabbitMQ exactly-once，Consumer 必须幂等。

## 7. 后端基线

- Java 21；
- Spring Boot 4.1.x；
- Spring AI 2.0.x；
- Spring MVC + SSE；
- MyBatis-Plus + MySQL 8.4 + Flyway；
- `spring.threads.virtual.enabled=false`；
- Java package 使用 `knowflow.sanjin.modules.<feature>...`；
- 数据库 migration 使用显式 SQL；
- MySQL 集成测试使用 Testcontainers，不使用 H2 模拟；
- API 基础路径 `/api/v1`；
- 错误采用 Problem Details + 稳定 `errorCode` + `correlationId`；
- API 中 BIGINT ID 序列化为字符串。

## 8. 前端基线

- Vue 3 + TypeScript + Vite；
- Composition API 与 `<script setup lang="ts">`；
- Vue Router、Element Plus、Vitest；
- 使用 npm 和已提交的 `package-lock.json`；
- 前后端是可独立构建、测试和部署的两个工程；
- “薄前端”只表示控制功能范围，不允许改成 Thymeleaf、JSP 或静态后端模板；
- AI/知识 Markdown 禁止未经净化的原始 HTML。

## 9. Owner 与安全边界

- V1 不实现应用内登录或 Spring Security。
- 统一通过 `CurrentOwnerProvider` 获取固定 System Owner `id=1`。
- Controller 不接受客户端传入 userId/ownerId。
- MySQL、Redis key、RabbitMQ task 和 Qdrant payload/filter 都必须保留 owner 边界。
- 无认证版本只能运行在 localhost、可信内网或已有外层保护的环境，不能裸露公网。

严禁提交或输出：

- `.env` 和真实 Secret；
- 模型 API Key 或主加密密钥；
- 数据库密码；
- 私钥、Token；
- 完整私人聊天、知识正文、Prompt 或上传文件内容到默认日志；
- 本地数据目录、构建产物和 IDE/系统临时文件。

配置示例只能使用无效占位符。Model API Key 不得进入 Message、Qdrant payload、错误响应或 Git 历史。

## 10. 文件修改与 Git

- 保留用户现有修改，不覆盖不属于当前任务的变更。
- 修改前先定位现有实现和文档，不猜测文件位置。
- 不使用破坏性 Git 命令，不执行 `git reset --hard` 或无授权文件删除。
- 不自动提交、推送或创建 PR，除非用户明确要求。
- 第一次正式提交前必须确认 `.gitignore` 排除了 `.DS_Store`、`.env`、IDE 文件、`target/`、`node_modules/`、`dist/`、日志和本地运行数据。
- 架构或产品决策变化需要更新文档；跨 Phase 稳定决策变化还需要 ADR 和 Owner 确认。

## 11. 测试原则

- 测试应证明业务规则、事务、隔离、幂等和失败恢复，不只覆盖 happy path。
- 默认单元测试和 CI 不调用真实云端模型。
- 云端 ChatModel、Utility Model 和 Embedding 的 smoke/eval 必须显式运行并避免泄露 Secret。
- 集成测试逐步使用 Testcontainers MySQL、Redis、RabbitMQ、Qdrant。
- E2E 最终必须通过正式 Vue 前端完成两个 V1 核心闭环。
- 不使用固定长时间 sleep 掩盖异步时序问题。

## 12. 任务交付说明

实现任务的最终交付至少说明：

- 完成了什么；
- 未完成且明确排除什么；
- 主要调用链；
- 事务和外部依赖边界；
- 失败与恢复语义；
- 修改的关键文件；
- 已运行的验证及结果；
- 是否存在需要后续确认的风险。

Review 任务必须先报告 Findings；每条包含证据位置、触发场景、影响、违反的规则、最小修复方向和缺失测试。没有阻断问题时也要明确说明剩余测试风险。
