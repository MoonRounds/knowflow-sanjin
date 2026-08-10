# KnowFlow Frontend

KnowFlow 的独立 Vue 3 + TypeScript 前端工程，使用 Vite、Vue Router、Element Plus 和 Vitest。

## 当前范围

当前处于 Phase 1 实施中：`/chat` 仍是后端健康连通页，`/knowledge-bases` 已有 CRUD 初版和
页面/API 交互测试；OpenAPI 快照与手动漂移检查已经建立，类型自动生成和 CI 契约检查仍待完成。
不要提前加入 Chat、Model Settings、Candidate、Processing 或 Upload 的空页面。

## 目录

```text
frontend/
├── public/             # 直接复制到构建输出的静态资源
├── src/
│   ├── api/            # 薄 fetch client 及相邻单元测试
│   ├── assets/         # 由源码导入的资源
│   ├── __tests__/      # 路由页面和组件交互测试
│   ├── router/         # Vue Router 配置
│   ├── views/          # 路由级页面
│   ├── App.vue
│   └── main.ts
├── package.json
├── vite.config.ts
└── vitest.config.ts
```

出现真实跨页面状态后再引入 `stores/`；组件被多个页面复用后再进入 `components/`，不创建空目录。

## 命令

```bash
npm ci
npm run dev
npm run typecheck
npm run test
npm run lint
npm run format:check
npm run build
```

开发服务器默认运行在 http://localhost:5173，并将 `/api` 代理到
http://127.0.0.1:8080。API 基础路径可通过 `VITE_API_BASE` 覆盖。
本地覆盖时将 `.env.example` 复制为 `.env.local`，不要提交本地环境文件。
