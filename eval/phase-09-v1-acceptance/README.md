# Phase 09 V1 验收固定数据

两个 V1 核心闭环（对话沉淀闭环、个人笔记/上传闭环）的固定、可重复验收数据。
内容全部为公开合成数据，专有命名不与通用模型知识混淆；不含真实聊天、私人知识正文、Prompt 或 Secret。

## 用途

- **确定性系统断言**（进 CI）：后端集成测试 + Playwright E2E 用 stub 驱动，断言状态迁移、
  owner/KB filter、检索来源、cited 标记、任务终态等可验证事实。
- **LLM 质量观察**（不进 CI）：真实模型验收时观察 RAG 是否命中、来源是否相关；允许合理波动，
  不以任意问题 100% 命中为目标。

## 命名约定

固定使用带 `Kf-` 前缀的专有名称，避免与通用模型知识混淆（例如问「Kf-海豚-部署手册」而非
「如何部署」）。以下 ID/名称在两个闭环中固定复用。

- 知识库：`Kf-后端工程规范`、`Kf-个人知识管理`
- 笔记标题：`Kf-海豚-部署手册`、`Kf-番茄工作法-个人实践`
- 上传文件：`kf-dolphin-deploy.md`、`kf-pomodoro-notes.txt`
- 会话标题：`Kf-验收-对话沉淀`、`Kf-验收-检索个人笔记`
- 问题：见各闭环「固定问题集」

## 文件索引

| 文件 | 内容 |
|------|------|
| `conversation-loop.md` | 对话沉淀闭环：步骤、数据、断言 |
| `note-loop.md` | 个人笔记/上传闭环：步骤、数据、断言 |
| `assertion-matrix.md` | 固定断言 ID 与默认自动化证据映射 |
| `acceptance-record-template.md` | V1 验收记录模板（Step F 使用） |
| `RESULTS-2026-08-11.md` | 当前本地确定性验收结果与尚未执行的发布门禁 |

默认确定性执行入口是 `sh scripts/verify-e2e.sh`；完整组合入口是
`sh scripts/verify-all.sh`。两者只能使用本地 stub 与隔离 E2E 基础设施，不读取真实 Provider Secret。
