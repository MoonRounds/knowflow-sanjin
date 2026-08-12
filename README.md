# KnowFlow

个人 AI 学习与知识沉淀系统。KnowFlow 帮助个人用户在与 AI 的对话中提取、整理、索引和检索知识，形成「对话沉淀」与「个人笔记」两个完整的学习闭环。

V1 双闭环已通过真实前端 E2E 验收并合入主分支（Phase 9）。

## 核心概念

KnowFlow 围绕一条知识生命周期运转：**产生 → 沉淀 → 索引 → 检索 → 再利用**。

| 概念 | 说明 |
|---|---|
| **KnowledgeBase** | 逻辑知识域，相当于一个"文件夹"或"学科"。同一知识库下所有内容在聊天中可被检索。 |
| **KnowledgeItem** | 一条独立的知识条目，可来自对话提取、手写笔记或上传文件。 |
| **Candidate** | 对话中 AI 建议沉淀的知识草稿，需你审核、编辑后确认才成为 Item。 |
| **ModelConfig** | 云端 OpenAI-Compatible 文本模型配置（如 DeepSeek、Qwen），含 Base URL 与 API Key。 |
| **RAG** | 检索增强生成：提问时先检索相关 Item，再把上下文交给模型生成带引用的回答。 |

细节与领域规则见 [docs/plans/DECISIONS.md](docs/plans/DECISIONS.md)。

## 两个核心闭环

KnowFlow V1 的成功标准是以下两条真实前端闭环：

```text
配置并选择 ChatModel
→ SSE 多轮聊天
→ 用户显式触发会话级知识提取
→ AI 产生 0～N 个 KnowledgeCandidate
→ 用户审核、编辑、确认 → KnowledgeItem
→ 异步 Chunk / Embedding / Qdrant Index
→ 新会话自动 Router → RAG 检索与来源展示
```

```text
创建 KnowledgeBase
→ 创建 Manual Note 或上传 Markdown/TXT → KnowledgeItem
→ 解析 / Chunk / Embedding / Index
→ 新会话自动 Router → RAG 检索与来源展示
```

## 当前状态

**V1 已达成**：Phase 0–9 全部合入，双闭环通过 Playwright E2E 验收。分阶段路线见 [docs/plans/README.md](docs/plans/README.md)。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21 · Spring Boot 4.1 · Spring AI 2.0 · Spring MVC + SSE · MyBatis-Plus |
| 前端 | Vue 3 + TypeScript + Vite · Vue Router · Element Plus · Vitest · Playwright |
| 数据 | MySQL 8.4（事实源）· Redis（Chat Memory 投影）· RabbitMQ（异步任务）· Qdrant（向量索引） |
| 构建 | Maven Wrapper（后端）· npm（前端）· Docker Compose |

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

后端 Java 包采用 `common + modules` 的模块化结构，业务模块内部区分 Controller、Service、Mapper、Entity、DTO、VO、Assembler 和 Exception。完整约定见 [docs/architecture/backend-package-structure.md](docs/architecture/backend-package-structure.md)。

## 架构概览

对话和笔记产出的知识，经异步任务统一 Chunk / Embedding 后写入 Qdrant；新会话提问时由 Router 决定是否需要 RAG，检索命中后由模型带引用回答。MySQL 是唯一事实源，Redis / Qdrant 均可从事实数据重建。

```text
                    ┌─────────────┐
    Vue 前端 ──SSE──▶  Chat/Router │
                    └──────┬──────┘
                           │
   ┌───────────────┬───────┴────────┐
   ▼               ▼                ▼
 MySQL (事实源)   Qdrant (向量)    Redis (Memory)
   ▲  ▲  ▲                            ▲
   │  │  └──── Chat Memory 投影 ──────┘
   │  └── 异步 Index / Extraction ────┘ (RabbitMQ)
   └── 上传 / 笔记 / 对话提取
```

系统上下文与后端分层见 [docs/architecture/system-context.md](docs/architecture/system-context.md)。

## 快速开始

以下流程让你在本地完整跑通前端 + 后端 + 全部依赖。默认使用 **本地模型 Stub**，**不需要任何真实 API Key** 即可体验全部功能；需要真实模型时见 [配置真实模型](#配置真实模型)。

### 环境要求

- Java 21（推荐 Eclipse Temurin）
- Node.js 22+，npm 10+
- Docker + Docker Compose（用于 MySQL、RabbitMQ、Qdrant；Redis 见下）

### 第一步：启动基础设施

```bash
cp .env.example .env

# 启动 MySQL、RabbitMQ、Qdrant
docker compose up -d mysql rabbitmq qdrant

# 主 Compose 未包含 Redis（Chat Memory 用），单独补起一个：
docker run -d --name knowflow-redis -p 6379:6379 redis:7-alpine
```

> **关于 Redis**：它是多轮对话记忆的投影层，缺失时后端自动降级、从 MySQL 重建，不会启动失败。不装也可正常体验，只是多轮记忆失效。

### 第二步：启动后端

```bash
# 生成并导出本地主密钥。请稳定保存在本机 Secret 管理方式中；
# 更换或丢失后，已保存的 ModelConfig API Key 将无法解密。
export KNOWFLOW_SECURITY_MASTER_KEY="$(openssl rand -base64 32)"

./knowflow-app/mvnw -f pom.xml -pl knowflow-app spring-boot:run
```

> `KNOWFLOW_SECURITY_MASTER_KEY` 是**唯一硬性必需**的环境变量，缺失时后端直接拒绝启动。注意 **Spring Boot 不会自动读取根目录 `.env`**，Compose 才会——上面的 `export` 不能省。

后端启动后确认健康：

- http://localhost:8080/actuator/health
- http://localhost:8080/api/v1/health

### 第三步：启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器运行在 **http://localhost:5173**，`/api` 已代理到后端 8080，无需关心跨域。

### 第四步：零成本体验（本地 Stub 模式）

不配任何真实 Key 就能在界面里跑通全部功能：

```bash
# 终端一：启动本地 OpenAI-Compatible 模型 Stub（Chat / Router / Embedding，固定 1024 维）
python3 scripts/model-stub.py

# 终端二：重启后端，允许连接 localhost 模型目标
export KNOWFLOW_MODEL_ALLOW_LOCAL_BASE_URL=true
./knowflow-app/mvnw -f pom.xml -pl knowflow-app spring-boot:run
```

然后在浏览器打开 http://localhost:5173：

1. **模型设置**页面创建一个 ModelConfig，指向 `http://127.0.0.1:18080/v1`，模型名随便填（Stub 不校验）；
2. 在 **Embedding** 配置项填入 `KNOWFLOW_EMBEDDING_BASE_URL=http://127.0.0.1:18080/v1`，API Key 留空（Stub 不校验）。

Stub 会返回预设的固定演示内容，用来验证 RAG 链路是否打通，详见下文[自测指南](#自测指南)。

## 配置真实模型

Stub 只能验证链路。要用真实模型对话与检索，配置如下。

### 全局 Embedding（系统级）

复制 `.env.example` 为 `.env` 并填入真实值：

```bash
KNOWFLOW_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
KNOWFLOW_EMBEDDING_API_KEY=<你的 DashScope Key>
```

> Embedding 是单一系统级云端配置，不进入 ModelConfig 页面。留空时索引任务会因「Base URL 为空」失败并重试——真实使用必须提供。

### ChatModel（ModelConfig 页面）

在界面的**模型设置**中创建，各提供一份可直接复制的配置：

| 字段 | DeepSeek | Qwen |
|---|---|---|
| Provider | `DeepSeek` | `Qwen` |
| Base URL | `https://api.deepseek.com/v1` | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| Model Name | `deepseek-chat` | `qwen-plus` |
| temperature | `0.7` | `0.7` |
| maxOutputTokens | `2048` | `2048` |

API Key 在界面输入后加密保存，接口只返回掩码。保存时若模型不可用会给出提示；配置错误的模型在聊天中会以稳定错误码提示。

## 自测指南

启动后数据库为空，需要先建内容。以下两条路径对应 V1 的两个核心闭环。

### 闭环一：个人笔记 → RAG 检索

1. **知识库**页创建知识库（如「个人知识管理」）；
2. **知识库详情**页新建一条 Manual Note，写一段有意义的内容，保存；
3. 等待右侧/任务状态显示**索引完成**（异步 Chunk + Embedding + 写入 Qdrant）；
4. 打开**新会话**，问一个和笔记内容相关的问题；
5. 回答中应出现 **引用来源**（编号对应本次检索到的笔记），来源面板可见。

### 闭环二：对话沉淀 → RAG 检索

1. 在**模型设置**中确认已启用一个可用的 ChatModel（真实或 Stub 皆可）；
2. **聊天**页与 AI 多轮对话，聊到一个有沉淀价值的知识点；
3. 点击**提取知识**，等待 AI 产生 0～N 个 **Candidate**；
4. 在**候选**页审核、编辑，**确认**为 KnowledgeItem（也可拒绝）；
5. 等待异步索引完成，新会话中提问，应能检索到该知识点并带来源。

### 一键整栈验证（可选）

`sh scripts/verify-e2e.sh` 用隔离 Compose + 本地模型 Stub + Playwright 自动跑完两条闭环并建出演示内容，**不需要真实 Key**，适合快速验证整栈健康。

## 验证脚本

仓库级验证入口以 `scripts/` 为准，GitHub Actions 也只调用它们：

```bash
sh scripts/verify-fast.sh             # 快速验证：编译、单元测试、typecheck、lint、format、build（无需 Docker）
sh scripts/verify-integration.sh      # MySQL/Redis/RabbitMQ/Qdrant Testcontainers 集成测试（需 Docker）
sh scripts/verify-e2e.sh              # 隔离基础设施 + Stub + Playwright 双闭环 E2E
sh scripts/verify-failure-drills.sh   # 定向 Retry/DLQ/恢复演练
sh scripts/verify-all.sh              # 完整验证组合（fast → secret scan → integration → e2e）
sh scripts/check-tracked-secrets.sh   # 扫描被跟踪/待跟踪文件的私钥、云 Key、token
sh scripts/check-api-contract.sh      # 后端运行时检查 OpenAPI 快照漂移
sh scripts/check-generated-api-types.sh # 前端 generated API 类型与 OpenAPI 快照漂移检查
```

真实 Provider 冒烟（**不进入 CI**，需真实 Key）：

```bash
sh scripts/live-smoke.sh DeepSeek <config-id>
sh scripts/live-smoke.sh Qwen <config-id>
sh scripts/embedding-smoke.sh
```

> `verify-fast.sh` 不要求 Docker；`verify-integration.sh` 需要 Docker。完整的测试原则见 [docs/development/testing.md](docs/development/testing.md)。

## 故障排查（FAQ）

| 症状 | 原因与解法 |
|---|---|
| 后端启动报 `KNOWFLOW_SECURITY_MASTER_KEY is not configured` | 未设置主密钥。运行 `export KNOWFLOW_SECURITY_MASTER_KEY="$(openssl rand -base64 32)"` 后再启动。 |
| `docker compose up` 提示卷路径不存在 | Compose 数据卷硬编码了 `/Users/sanjin/docker/...` 本机路径，换机器需在 `docker-compose.yml` 中改成本机目录。 |
| 启动后端时连不上 MySQL/RabbitMQ/Qdrant | 确认先执行 `docker compose up -d mysql rabbitmq qdrant`，且端口未被占用。 |
| 聊天 / 检索报「模型 Base URL 为空」 | Embedding 或 ChatModel 未配置。真实 Key 见[配置真实模型](#配置真实模型)，Stub 见[零成本体验](#第四步零成本体验本地-stub-模式)。 |
| RAG 没有检索到内容 | ① 提问前先确认已创建知识库与 Item，并等异步索引完成；② 提问内容与笔记相关性太弱；③ 没有可用 ChatModel/Embedding。 |
| 前端 `npm run dev` 后接口 404 | 确认后端已启动在 8080；前端 `/api` 已代理到 8080。 |
| 数据想重置 | MySQL 数据在 `docker compose down -v` 后清空；`-v` 会连同命名卷一并删除。 |

## 安全说明

- V1 为**单用户本地系统**，不实现登录认证。
- 数据库凭据与应用主密钥通过环境变量注入；Provider API Key 在 ModelConfig 保存为认证密文。
- `.env` 已被 Git 忽略，且不会被 Spring Boot 自动读取；`.env.example` 是唯一允许提交的 env 示例文件。
- 所有业务数据与向量索引均保留 owner 边界，固定 System Owner `id=1`。
- 无认证版本只能运行在 **localhost / 可信内网 / 已有外层保护**的环境，不能裸露公网。
- 禁止将密钥、证书或真实配置提交到仓库。

## 文档

- [KnowFlow V1 开发路线图](docs/plans/README.md)
- [V1 决策基线](docs/plans/DECISIONS.md)
- [执行与 Review 工作流](docs/plans/WORKFLOW.md)
- [产品范围](docs/product/v1-scope.md)
- [系统上下文](docs/architecture/system-context.md)
- [后端包结构](docs/architecture/backend-package-structure.md)
- [Maven 父子工程决策](docs/adr/0001-maven-parent-reactor.md)
- [开发与测试指南](docs/development/testing.md)
