# 系统上下文

KnowFlow 是一个模块化单体应用。下图描述 V1 目标架构，不表示所有组件已在当前 Phase 实现。

## 架构概览

```text
┌─────────────────────────────────────────────────┐
│                   Browser                       │
│         (Vue 3 SPA on localhost:5173)           │
└────────────────────┬────────────────────────────┘
                     │ HTTP / SSE
                     ▼
┌─────────────────────────────────────────────────┐
│              Spring Boot (knowflow-app)         │
│              API: /api/v1                       │
│                                                  │
│  ┌──────────┐ ┌──────────┐ ┌───────────────┐   │
│  │ Chat     │ │Knowledge │ │ Model Config  │   │
│  │ Controller│ │Controller│ │ Controller    │   │
│  └────┬─────┘ └────┬─────┘ └───────┬───────┘   │
│       │             │               │            │
│  ┌────┴─────────────┴───────────────┴───────┐   │
│  │            Service Layer                  │   │
│  └────┬─────────────┬───────────────┬───────┘   │
│       │             │               │            │
│  ┌────┴─────┐ ┌─────┴──────┐ ┌─────┴────────┐  │
│  │ MyBatis  │ │  Redis     │ │  Processing   │  │
│  │ (MySQL)  │ │  (Memory)  │ │  (RabbitMQ)   │  │
│  └──────────┘ └────────────┘ └───────────────┘  │
│                                                  │
│  ┌──────────┐ ┌──────────┐                       │
│  │ Qdrant   │ │ AI Model │                       │
│  │ (Vector) │ │ (Cloud)  │                       │
│  └──────────┘ └──────────┘                       │
└──────────────────────────────────────────────────┘
```

后端代码采用业务模块优先、模块内分层的包结构，详见
[后端包结构](./backend-package-structure.md)。

当前 Phase 1 只落地 MySQL、System Owner 与 KnowledgeBase 后端切片；Redis、RabbitMQ、
Qdrant、AI Model、Chat 和 Model Config 均由后续 Phase 按计划引入。

## 外部依赖

| 组件 | 用途 | V1 必需 |
|------|------|---------|
| MySQL 8.4 | 业务数据事实源 | 是 |
| Redis | 聊天记忆缓存 | 否(可降级) |
| RabbitMQ | 异步任务队列 | 否(可降级) |
| Qdrant | 向量索引 | 否(可降级) |
| OpenAI-Compatible API | Chat/Embedding/Utility | 是 |

## 端口

| 端口 | 服务 |
|------|------|
| 8080 | Spring Boot API |
| 5173 | Vite Dev Server |

## 部署

V1 仅支持 localhost 或可信内网部署。所有外部端口默认绑定 127.0.0.1。
