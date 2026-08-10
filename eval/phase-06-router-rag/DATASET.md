# Phase 6 Eval：Knowledge Router / Retrieval / RAG

固定、可重复的 V1 评估样例。内容全部为公开合成数据，不含真实聊天、私人知识正文、Prompt 或 Secret。

## 用法

- **确定性链路测试**：`RagVerticalSliceIT`（后端集成测试）用 stub 驱动 Router/Embedding/ChatModel，断言
  selected KB、owner+KB filter、topK、`[Sx]` cited 匹配。属于 `scripts/verify-integration.sh` 的一部分。
- **Router/Retrieval 单元测试**：`RouterServiceTest`、`RetrievalServiceTest`、`RagContextBuilderTest`
  （mock 协作对象，无云端调用）。属于 `scripts/verify-fast.sh` 的一部分。
- **可选真实模型 smoke**：显式运行，不进入 CI。见下方「真实模型 smoke」。

## 固定知识样本（合成）

1. **Spring 事务传播行为**（KB: 后端开发）
   - 正文：REQUIRED 加入外层事务；REQUIRES_NEW 总是开启新事务；PROPAGATION_REQUIRES_NEW 会暂停外层事务。
2. **部署检查清单**（KB: 运维规范）
   - 正文：上线前必须备份数据库、确认环境变量、回滚预案；禁止公网裸露无认证服务。
3. **项目命名约定**（KB: 工程规范）
   - 正文：Java 根包为 `knowflow.sanjin`；业务代码使用 `modules.<feature>`；不使用 Lombok/MapStruct。
4. **Chat Memory 与知识库区别**（KB: 架构决策）
   - 正文：Chat History ≠ Chat Memory ≠ Knowledge Base；MySQL 是事实源，Redis 投影可重建。

## 固定问题集与预期

| 编号 | 问题 | 预期 needRag | 预期选中 KB | 预期 filter | 预期来源 |
|------|------|------------|-----------|------------|---------|
| Q1 | Spring 的 REQUIRED 传播行为是什么？ | true | 后端开发 | owner + KB=后端开发 | 样本 1 |
| Q2 | 项目 Java 包名规范和禁止的库？ | true | 工程规范 | owner + KB=工程规范 | 样本 3 |
| Q3 | 你好 | false | - | 无检索调用 | - |
| Q4 | 随便讲个冷笑话 | true（需判断） | 无相关 | owner + 选中 KB | 无（NO_RELEVANT_CONTEXT） |

## 真实模型 smoke

```bash
# 前置：已配置 Utility Model、Embedding、Qdrant 与至少一条已索引 Manual Note
# 显式运行，不入 CI；不得写入 Key 到任何文件
# 通过已有脚本模式扩展 RAG 路径（当前在 eval/phase-06-router-rag/RESULTS.md 记录基线）
```

Eval 允许模型结果合理波动，不以任意问题 100% 命中为目标。
