# 后端包结构

KnowFlow 后端采用模块化单体与“业务模块优先、模块内按职责分层”的包结构。该结构参考
`interview-guide` 的 `common / modules / infrastructure` 边界，但统一了业务模块内部的
Controller、Service、Mapper、Entity、DTO 和 VO 位置。

## 当前目录

```text
knowflow-app/src/main/java/knowflow/sanjin/
├── KnowFlowApplication.java               # 唯一启动类，位于组件扫描根包
├── common/                                # 跨业务模块的技术能力
│   ├── config/                            # Spring/MyBatis/Secret/模型客户端 配置类
│   ├── controller/                        # 非业务模块的通用 HTTP 入口
│   ├── exception/                         # 全局异常转换
│   ├── security/                          # 加密、脱敏、Base URL SSRF 校验
│   └── util/                              # 已被复用的无状态工具；不放业务规则
└── modules/                               # 业务模块
    ├── owner/
    │   ├── entity/
    │   └── service/
    ├── knowledge/
    │   ├── controller/                    # KnowledgeItem / Manual Note REST
    │   ├── dto/                           # Manual Note 创建/更新请求
    │   ├── entity/                        # KnowledgeItem / Tag / 关联 / KnowledgeChunk
    │   ├── exception/                     # 模块业务异常与索引错误分类
    │   ├── infrastructure/                # Embedding / Qdrant 薄客户端
    │   ├── mapper/
    │   ├── service/                       # KnowledgeService / KnowledgeIndexingService / TextChunker
    │   └── vo/
    ├── processing/
    │   ├── assembler/
    │   ├── controller/                    # Processing 轻量列表与手动 Retry
    │   ├── entity/                        # ProcessingTask（兼轻量 Outbox）
    │   ├── exception/
    │   ├── listener/                      # IndexTaskConsumer（RabbitMQ）
    │   ├── mapper/
    │   ├── scheduler/                     # 恢复扫描
    │   ├── service/                       # 任务状态机 / 提交 / 发布
    │   └── vo/
    ├── knowledgebase/
    │   ├── assembler/                     # Entity 与 API 模型的显式转换
    │   ├── controller/                    # HTTP 入站与校验
    │   ├── dto/                           # 请求 DTO
    │   ├── entity/                        # MySQL 持久化实体
    │   ├── exception/                     # 模块业务异常
    │   ├── mapper/                        # MyBatis Mapper
    │   ├── service/                       # 应用服务与事务边界
    │   └── vo/                            # API 响应 VO
    └── modelconfig/                       # ModelConfig、Revision、Owner AI Settings
        ├── assembler/
        ├── controller/
        ├── dto/
        ├── entity/
        ├── exception/
        ├── mapper/
        ├── service/                       # 含 ModelClientFactory / ModelCapabilityService
        └── vo/
    └── rag/                              # Knowledge Router / Retrieval / RAG 编排（Phase 6）
        ├── dto/                           # RouterResult / RouterTrace / RetrievedSource / RagContext
        ├── exception/                     # Router 失败异常（降级用）
        └── service/                       # RouterService / RetrievalService / RagContextBuilder
    └── extraction/                       # 会话知识提取与候选审核（Phase 7）
        ├── config/                        # ExtractionProperties（knowflow.extraction.*）
        ├── controller/                    # ExtractionController / CandidateController + Assembler
        ├── dto/                           # ExtractionResult（Structured Output schema）/ 草稿请求
        ├── entity/                        # KnowledgeExtractionTask / KnowledgeCandidate
        ├── exception/                     # 预算拒绝 / 状态迁移 / 失败分类异常
        ├── listener/                      # ExtractionTaskConsumer（RabbitMQ 独立工作队列）
        ├── mapper/
        └── service/                       # ExtractionService / ExtractionExecutor / CandidateService / CandidateConfirmService
    └── document/                         # Markdown/TXT 上传、去重与文档解析（Phase 8）
        ├── config/                        # DocumentProperties（knowflow.document.*）
        ├── controller/                    # DocumentController（上传/元数据/下载）+ FileMetadataAssembler
        ├── entity/                        # FileMetadata（与 KnowledgeItem 一对一）
        ├── exception/                     # 大小/类型/内容/存储缺失/解析失败异常
        ├── listener/                      # DocumentParseTaskConsumer（RabbitMQ 独立 document 工作队列）
        ├── mapper/
        └── service/                       # DocumentUploadService / DocumentParsingService / DocumentParser
                                            # MimeDetectionService(tika-core) / FileStorageService / LocalFileStore
```

测试代码镜像生产代码的模块路径；数据库容器等测试基础设施统一放在
`src/test/java/knowflow/sanjin/testinfra/`。

不依赖 Docker 的测试以 `Test` 结尾，由 Maven Surefire 执行；需要 MySQL Testcontainers 的
测试以 `IT` 结尾，由 Maven Failsafe 在 `verify` 阶段执行。

## 依赖方向

```text
Controller → Service → Mapper → MySQL
     │           │
     ├── DTO     ├── Entity
     └── VO      └── 模块 Exception

Assembler: Entity → VO
Common: 只能承载多个模块共享的技术能力
```

- `KnowFlowApplication` 根包只放启动类，确保 Spring 默认扫描所有子包。
- Controller 不直接访问 Mapper，事务放在 Service。
- 请求模型放 `dto`，响应模型放 `vo`；Entity 不作为 API 响应返回。
- Mapper 使用显式 `@Mapper`，不依赖扫描整个根包。
- 模块专属代码留在模块内；确认有多个模块复用后才能进入 `common`。
- 不创建空目录、空 Service、通用 Base CRUD 或未使用的扩展点。
- Java 约定名称是 `service`，不是 `server`。

## 新增模块时

先创建该 Phase 当前真正需要的职责目录。例如只有 Entity、Mapper 和 Service 时，不提前创建
Controller、DTO 或 VO。新增类型的测试放在相同模块路径下，并运行后端包结构测试和仓库验证脚本。
