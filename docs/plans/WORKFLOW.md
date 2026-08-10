# Phase 执行与独立 Review 工作流

本文档定义如何在相互隔离的 Codex 任务中执行 Phase、Review、修复与复审，避免长对话上下文污染后续实现。

## 一次 Phase 的推荐生命周期

```text
确认起始 commit
→ 新任务执行 PLAN
→ 本地验证全部通过
→ 提交 Phase 实现 commit
→ 新任务执行 REVIEW
→ 单独修复 Review Findings
→ 再次独立 Review
→ Owner 学习验收
→ 进入下一 Phase
```

## 执行任务规则

执行某个 Phase 时，新任务只需要获得：

- 仓库根目录 `AGENTS.md`；
- `docs/plans/DECISIONS.md`；
- 目标 Phase 的 `PLAN.md`；
- PLAN 明确引用的已有领域或 ADR 文档；
- 当前工作树与起始 commit。

执行 Agent 必须：

1. 先检查 Git 状态，保护用户已有修改。
2. 只实现 PLAN 的 In Scope。
3. 遇到超出决策基线的架构选择时停止并报告，不自行扩张。
4. 按 PLAN 的 checkpoint 小步实现和验证。
5. 使用 `scripts/` 中的仓库级验证入口。
6. 更新本阶段明确要求的文档，不顺带设计未来 Phase。
7. 最终说明调用链、事务边界、失败语义、验证结果和未实现范围。

### 通用执行提示词

可在新的 Codex 任务中使用：

```text
请执行 <PHASE_PLAN_PATH>。

开始前完整阅读：
1. AGENTS.md
2. docs/plans/DECISIONS.md
3. <PHASE_PLAN_PATH>

只实现该 Phase 的 In Scope，不提前实现后续 Phase。先检查 Git 状态并记录起始 commit。按照计划中的 checkpoint 逐步实现、运行验证，并在最终交付中说明调用链、事务边界、失败语义、测试结果和剩余限制。遇到需要改变已确认决策的情况时停止并向我确认。
```

## Review 任务规则

Review 必须在新的 Codex 任务中执行，避免 Review Agent继承实现过程中的假设。

Review 输入必须包含：

- Phase 起始 commit 或固定 base commit；
- Phase 完成 commit；
- `AGENTS.md`；
- `docs/plans/DECISIONS.md`；
- 对应 `PLAN.md` 与 `REVIEW.md`。

Review 默认只读：

- 不修改代码；
- 不升级依赖；
- 不顺手重构；
- 不把后续 Phase 的功能缺失当成本 Phase 缺陷；
- 只报告相对本 Phase 规格、仓库规范和回归风险的可操作问题。

Findings 按优先级分类：

- `P0`：数据破坏、Secret 泄漏、核心路径完全不可用；
- `P1`：违反 Phase 核心验收、事务/隔离/幂等严重错误；
- `P2`：真实边界缺失、重要可维护性或测试问题；
- `P3`：低风险改进，不阻塞阶段完成。

每条 Finding 必须包含：

- 文件与紧凑行号范围；
- 可复现或可证明的具体场景；
- 为什么违反 PLAN/DECISIONS/AGENTS；
- 建议修复方向，但不直接实施。

### 通用 Review 提示词

```text
请对 <BASE_COMMIT>..<PHASE_COMMIT> 执行独立 Review。
完整阅读：
1. AGENTS.md
2. docs/plans/DECISIONS.md
3. <PHASE_PLAN_PATH>
4. <PHASE_REVIEW_PATH>

本次只 Review，不修改文件。检查本 Phase 是否满足规格、仓库规范和回归安全。不要把后续 Phase 的明确排除项作为缺陷。先输出按 P0-P3 排序的 findings；若没有问题，明确写“未发现阻塞性问题”，并列出已运行的验证和仍未覆盖的风险。
```

## Review 后的修复

- 修复任务只处理已确认的 Findings。
- 若 Finding 暴露了规划缺陷，先更新 PLAN/DECISIONS 并获得 Owner 确认。
- 修复后重新运行该 Phase 的全部 Required Verification。
- 使用新的 Review 任务复审，不要求原实现任务“自证清白”。

## Owner 学习验收

每个 Phase 结束前，Owner 至少应能回答：

- 这个 Phase 解决了什么用户问题？
- 请求的完整调用链是什么？
- MySQL 事务边界在哪里？
- 外部依赖失败后数据库状态是什么？
- 哪些测试能证明核心规则？
- 为什么没有提前实现下一 Phase？

若这些问题无法回答，应先进行代码讲解或 CodeTour，再进入下一阶段。

