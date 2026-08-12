# 闭环 2：个人笔记/上传闭环验收数据

用户通过正式前端沉淀个人笔记（Manual Note 或上传 Markdown/TXT），并在新会话中检索到。

## 流程

```text
创建 KnowledgeBase
-> 创建 Manual Note 或上传 Markdown/TXT
-> 形成 KnowledgeItem
-> 文档场景异步解析
-> Chunk / Embedding / Qdrant Index
-> 索引成功
-> 新建 Conversation
-> Router 自动路由
-> RAG 检索个人笔记
-> AI 回答
-> 前端追溯 KnowledgeItem / 文件来源
```

## 固定数据

- 知识库：`Kf-个人知识管理`
- Manual Note 标题：`Kf-番茄工作法-个人实践`（正文为专有实践，不与通用番茄钟知识混淆）
- 上传文件（二选一至少验证一种）：
  - `kf-dolphin-deploy.md`（Markdown，标题来自 H1）
  - `kf-pomodoro-notes.txt`（TXT，标题来自文件名）

### 笔记正文（Manual Note）

```markdown
# Kf-番茄工作法-个人实践

我实践番茄工作法时固定使用 45 分钟工作 + 10 分钟休息。
每完成 4 个番茄后安排 30 分钟长休息。
关键：每日 9 点开始，上午完成 3 个深度番茄。
```

### 上传文件 kf-dolphin-deploy.md

```markdown
# Kf-海豚-部署手册

海豚服务部署三步：1) 备份数据库；2) 导出配置文件；3) 传输原始数据到新主机。
部署后必须运行健康检查脚本 verify.sh。
回滚预案：恢复数据库备份并回退配置。
```

### 上传文件 kf-pomodoro-notes.txt

```text
Kf-番茄-离线工作法
我的离线番茄工作法：断网后仍可用，把任务写在纸上，45 分钟专注一轮。
```

## 预期结果

- Manual Note：创建即 `ACTIVE`，提交 Index Task，终态 `INDEXED`。
- 上传文件：`FileMetadata` + `KnowledgeItem` 创建，Document Parsing Task → `SUCCEEDED`，
  解析后进入 Index Task → `INDEXED`。
- 前端可从 KnowledgeItem Detail 追溯来源类型（`MANUAL_NOTE` / `UPLOAD_FILE`）。

## 固定问题集（新会话检索）

| 编号 | 问题 | 预期 needRag | 预期选中 KB | 预期来源 |
|------|------|------------|-----------|---------|
| N-Q1 | Kf-番茄工作法-我用多少分钟一个番茄？ | true | Kf-个人知识管理 | 笔记 Item |
| N-Q2 | Kf-海豚-部署三步是什么？ | true | Kf-个人知识管理 | 上传 Item |

## 确定性断言（进 CI）

1. Manual Note Item 终态 `INDEXED`，`indexedVersion>=1`。
2. 上传文件：Document Parsing Task `SUCCEEDED`，FileMetadata 与 Item 一对一，`sourceType=UPLOAD_FILE`。
3. 上传文件名不参与去重身份：同内容不同文件名只产生一个 Item。
4. 新会话 N-Q1：`ragStatus=USED`，来源为笔记 Item，snippet 含 `45 分钟`。
5. 前端 KnowledgeItem Detail 展示来源类型与文件下载入口（`/files/{id}/download`）。
6. 全程 `ownerId=1` 边界。

## LLM 质量观察（手工验收，不进 CI）

- 真实模型对 N-Q1/N-Q2 的回答是否命中刚沉淀的个人笔记（而非通用知识）。
- Router 是否只选中 `Kf-个人知识管理`。
