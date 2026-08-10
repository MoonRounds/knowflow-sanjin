# Phase 1 PLAN：System Owner 与 KnowledgeBase 全栈切片

## 目标

引入第一个真实持久化业务切片，验证 MySQL、Flyway、MyBatis-Plus、Owner 边界、OpenAPI 和前端 CRUD 的完整链路。

## 前置条件

- Phase 0 已完成独立 Review，阻塞问题已关闭。
- `scripts/verify-fast.sh` 通过。
- 记录 Phase 1 起始 commit。

## In Scope

- MySQL 8.4 LTS 开发容器和 Testcontainer。
- Flyway 与 MyBatis-Plus Boot 4 Starter。
- System Owner 和 `CurrentOwnerProvider`。
- KnowledgeBase 创建、列表、详情、编辑、启用/禁用、软删除。
- Knowledge 一级页面和真实前后端交互。
- OpenAPI 导出、前端 TypeScript 类型生成和契约检查基础。

## Out of Scope

- KnowledgeItem、Tag、Candidate、Chunk。
- ChatModel、Conversation、Redis、RabbitMQ、Qdrant。
- 登录或从客户端接收 userId。
- KnowledgeBase 树形结构、统计 Dashboard。

## 数据模型方向

### `app_user`

- BIGINT 主键。
- Flyway 初始化 `id=1` System Owner。
- 只包含展示名称、状态和 UTC 审计时间。
- 不包含密码、角色或认证字段。

### `knowledge_base`

- Owner 外键。
- display name、normalized name、description、enabled。
- lifecycle/soft delete 字段、rowVersion、UTC 审计时间。
- 同一 Owner 下 active normalized name 唯一。

所有 API ID 返回字符串。

## API 行为

- Controller 不接受 userId。
- Service 从 `CurrentOwnerProvider` 获取 Owner 并校验资源归属。
- 成功直接返回 DTO；失败使用 Problem Details。
- 规范化名称处理 trim 与英文大小写差异，保留展示名称。
- 禁用不等于删除。
- 本阶段尚无 Item，删除规则先实现软删除和基本约束；Phase 5 增加“不能造成孤儿 Item”的完整规则。
- rowVersion 冲突返回 409。

## 前端范围

- 一级导航出现 Knowledge。
- `/knowledge-bases` 支持列表、创建、编辑、启用/禁用和删除确认。
- 空状态清楚说明尚无知识库。
- 不出现 Manual Note、Upload 或 Item 假入口。

## 执行 Checkpoints

### Checkpoint A：Infrastructure and Migration

1. 只增加 MySQL Compose 服务，端口绑定 localhost，固定 8.4 LTS 版本。
2. 引入 Flyway、MyBatis-Plus、MySQL Driver、MySQL Testcontainers。
3. 创建 app_user 与 knowledge_base Migration。
4. 从空库运行 Migration，并验证外键/唯一约束。

### Checkpoint B：Owner Boundary

1. 实现简单 `CurrentOwnerProvider`，V1 返回 System Owner ID。
2. 资源访问始终经过 Owner 校验。
3. 测试 fixture 建立第二 Owner，验证跨 Owner 不可访问。

### Checkpoint C：KnowledgeBase Backend

1. API DTO、显式 Assembler、Application Service、Mapper。
2. 明确事务边界。
3. 完成 CRUD、禁用、软删除、乐观锁和错误码。
4. 不创建无用 Service Interface 或通用 Base CRUD。

### Checkpoint D：Contract and Frontend

1. 建立 OpenAPI 输出和契约检查。
2. 前端生成 TypeScript 类型并使用薄 fetch Client。
3. 实现 KnowledgeBase 页面与交互。
4. 更新根验证脚本。

## Required Verification

- Phase 0 全部验证。
- Flyway 从空 MySQL 8.4 成功执行。
- Mapper/事务/约束使用 Testcontainers MySQL，禁止 H2。
- 跨 Owner、重名、乐观锁、软删除、enabled 行为测试。
- Controller/API Problem Details 测试。
- 前端组件和 API 交互测试。
- OpenAPI 导出与 TypeScript 类型无漂移。
- 手动完成 KnowledgeBase 创建、编辑、禁用、删除。

## 验收标准

- 前端能够真实管理 KnowledgeBase。
- userId 不出现在请求 DTO。
- 数据库约束与 Service 规则同时有效。
- 当前只存在本阶段需要的两张业务表。
- MySQL 以外的中间件尚未加入。

## Phase Handoff

讲清：Controller → Application Service → Mapper → MySQL 调用链、事务位置、Owner 隔离、名称唯一性、乐观锁和软删除语义。

