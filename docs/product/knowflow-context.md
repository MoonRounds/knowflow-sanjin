我准备从 0 开发一个个人 Java 项目，项目暂定名：

# knowflow-sanjin

现在仓库基本还是空的。

在正式开始写代码之前，我已经围绕产品需求、系统架构和技术选型进行了一轮比较完整的思考。下面这些内容是目前已经确定的**项目上下文和设计倾向**。

你先不要把它们机械地当成最终实现方案，也不要立即写大量代码。

我希望你先完整理解我的想法，再结合实际工程情况进行分析和延伸，最终由你继续帮助我制定合理的项目设计与开发计划。

------

# 一、项目背景

这是我真正准备从 0 开发的个人项目。

项目开发过程中，我会大量依赖 Codex 等 AI 工具辅助编码，因为我目前无法完全独立手写这么完整的系统。

但是我的目标不是：

> AI 把代码全部生成出来，然后项目能跑就算完成。

而是：

> 借助 AI 完成开发，同时让我真正理解需求、架构、数据库、调用链、技术选型、异常场景和设计取舍。

最终我希望能够：

- 自己阅读和修改核心代码；
- 理解完整业务链路；
- 理解每个中间件为什么存在；
- 能解释主要数据库设计；
- 能解释 RAG、Memory、MQ 等核心机制；
- 面试时能够深入讲清楚项目，而不是只会介绍功能。

所以后续开发需要尽量：

**清晰、可解释、可维护、可测试、循序渐进。**

不要为了所谓“高级架构”过度设计。

------

# 二、产品定位

项目暂时定位为：

# KnowFlow —— 个人 AI 学习与知识沉淀系统

它不是单纯的大模型聊天套壳，也不是简单的“上传 PDF + RAG 问答”。

我真正想解决的问题是：

> 平时使用 AI 学习 Java、Redis、Spring、RAG 等技术时，会产生大量有价值的聊天内容，同时自己也会有 Markdown、TXT、PDF 等笔记。
>
> 这些知识目前非常分散，聊天结束以后很难再次组织和利用。
>
> 我希望系统能够把 AI 对话和个人笔记逐渐沉淀成自己的长期知识库，以后继续聊天时，AI 能够自动利用我以前积累的知识回答问题。

最终希望形成：

学习
→ AI 对话
→ 知识提取
→ 用户确认
→ 知识沉淀
→ RAG 检索
→ 再次辅助学习

这样的知识闭环。

------

# 三、核心用户场景

## 场景 1：普通多轮 AI 对话

例如：

用户：

> ConcurrentHashMap 为什么线程安全？

AI 回答以后，用户继续问：

> 那 JDK7 呢？

AI 应该理解“那”仍然指 ConcurrentHashMap。

所以系统必须支持真正的：

**Multi-turn Conversation / Chat Memory**

而不是每次请求都是完全独立的单轮问答。

------

## 场景 2：用户自由选择不同的大模型

用户可以在系统设置中配置自己的大模型，例如：

- DeepSeek
- OpenAI
- Qwen
- Ollama
- 其他兼容 OpenAI API 的模型

配置可能包括：

- Provider
- API Key
- Base URL
- Model Name
- Temperature 等

用户聊天时可以选择使用哪个模型。

因此系统需要考虑：

> 如何统一不同 ChatModel 的调用方式，而不是业务代码到处写不同厂商判断。

------

# 四、ChatModel 与 EmbeddingModel 需要分开

这是已经确定的重要原则。

ChatModel 可以由用户动态切换。

例如：

今天用 DeepSeek，明天用 Qwen。

但是：

**EmbeddingModel 不应该跟着聊天模型随意切换。**

因为不同 EmbeddingModel 产生的向量通常不是同一个向量空间。

所以目前思路是：

ChatModel：

> 用户可选择、可切换。

EmbeddingModel：

> 系统级或知识库级保持稳定。

以后如果真的更换 EmbeddingModel，需要考虑重新 Embedding 或 Vector Collection 隔离。

------

# 五、必须区分三个概念

项目里有三个非常容易混淆的东西：

# Chat History

表示：

> 用户完整聊过什么。

例如整个 Conversation 下的所有 User / Assistant Message。

主要用于：

- 历史会话展示；
- 消息回溯；
- 知识提取；
- 长期保存。

倾向保存到 MySQL。

------

# Chat Memory

表示：

> 当前 AI 对话需要记住什么。

例如：

第一轮问 ConcurrentHashMap，第二轮说“那 JDK7 呢”。

这里需要 Chat Memory 帮模型理解当前上下文。

倾向使用：

Spring AI Chat Memory + Redis。

------

# Knowledge Base

表示：

> 用户长期积累了什么知识。

例如：

三个月后新开一个会话：

> 我以前学习 ConcurrentHashMap 的时候总结了什么？

这时候应该通过 RAG 搜索长期个人知识。

因此必须始终保持：

Chat History
≠ Chat Memory
≠ Knowledge Base

三个概念职责不同。

------

# 六、个人知识库设计

用户可以建立多个逻辑知识库，例如：

- Java
- Redis
- Spring Boot
- RAG
- MySQL
- 计算机网络

但是：

**KnowledgeBase 是业务上的逻辑知识域。**

不代表 Java 一个物理 Vector Database、Redis 又一个 Vector Database。

底层可以共享统一 Vector Store，然后通过 Metadata 进行隔离和过滤。

------

# 七、一条知识可以属于多个 KnowledgeBase

这是一个比较重要的设计。

例如：

> Spring Boot 如何集成 Redis？

它显然不能简单归类成：

“Java”或者“Redis”。

它同时涉及：

- Spring Boot
- Redis

因此目前倾向：

KnowledgeItem 和 KnowledgeBase 使用多对多关系。

例如：

KnowledgeItem：

> Spring Boot 集成 Redis

所属 KnowledgeBase：

- Spring Boot
- Redis

Tags：

- RedisTemplate
- Lettuce
- Spring Data Redis

也就是说：

**KnowledgeBase 是大知识域，Tag 是更细粒度标签。**

------

# 八、Knowledge Router

我不希望用户每次聊天之前都必须手动选择：

> 这次使用 Java 知识库还是 Redis 知识库。

希望默认由系统自动判断。

例如：

用户问：

> 为什么 Redis 那么快？

Router 判断：

Redis 高相关。

如果用户问：

> Spring Boot 如何使用 Redis 实现分布式锁？

那么可能涉及：

- Redis
- Spring Boot

所以 Knowledge Router 应该支持：

> 一个问题对应 0～N 个 KnowledgeBase。

而不是强制只能选一个。

Router 同时需要判断：

> 当前问题是否需要使用 RAG。

例如：

> 你好。

这种问题没有必要访问知识库。

因此概念上可能会产生类似：

- needRag
- knowledgeBaseIds
- route scores

这样的结果。

V1 我倾向先考虑基于 LLM Structured Output 的 Router，不需要第一版就设计特别复杂的分类算法。

------

# 九、RAG 基本思路

当 Router 判断需要个人知识以后：

用户问题
→ Knowledge Router
→ 得到相关 KnowledgeBase
→ Query Embedding
→ Vector Search
→ Metadata Filter
→ Top-K Context
→ Prompt
→ ChatModel
→ 回答

Metadata 至少应该能够隔离：

- userId
- knowledgeBaseIds

后续可能还有：

- knowledgeItemId
- sourceType
- tags
- chunkIndex

------

# 十、AI 对话不能直接全部进入知识库

最开始我考虑：

用户问题 + AI 回答
→ 自动进入知识库。

后来认为这种设计不好。

因为聊天中会大量存在：

- “详细一点”
- “为什么？”
- “继续”
- 重复解释
- 低价值内容
- AI 可能产生的错误

如果每一轮都直接 Embedding，长期知识库会迅速变成垃圾场。

所以目前确定：

# AI 提取知识 + 用户确认

也就是：

AI 多轮聊天
→ AI 判断是否值得沉淀
→ 提取结构化 Knowledge Candidate
→ 用户确认
→ 正式 KnowledgeItem
→ Chunk
→ Embedding
→ Vector Store

AI 提取出来的信息可能包括：

- title
- summary
- content
- knowledgeBaseIds
- tags
- source information

用户可以：

- 确认；
- 修改；
- 拒绝。

只有确认后才真正进入长期个人知识。

------

# 十一、支持用户自己的笔记

个人知识不应该只来自 AI Conversation。

还需要支持用户上传自己的笔记。

V1 可以优先：

- Markdown
- TXT

后续再考虑：

- PDF
- Word

知识来源概念可以区分：

- AI_CONVERSATION
- UPLOAD_FILE
- MANUAL_NOTE

最终无论来自聊天还是上传文件，都能够进入统一 KnowledgeItem / Chunk / RAG 流程。

------

# 十二、当前技术栈倾向

目前经过讨论，暂定：

- Java
- Spring Boot
- Spring AI
- MyBatis-Plus
- MySQL
- Redis
- RabbitMQ
- Qdrant
- SSE
- Docker Compose

这些是当前倾向，不要求你完全机械执行。

如果实际开始设计后发现某个选择明显不合理，请指出原因和替代方案，而不是悄悄修改。

------

# 十三、MySQL 的职责

目前倾向让 MySQL 保存业务关系数据，例如：

- User
- ModelConfig
- Conversation
- Message
- KnowledgeBase
- KnowledgeItem
- KnowledgeCandidate
- KnowledgeItem / KnowledgeBase Relation
- Tag
- FileMetadata
- 异步任务状态

具体表结构还没有最终设计。

希望后续由你结合实际业务重新分析。

------

# 十四、为什么目前选择 MySQL + Qdrant

我已经有另一个：

Spring Boot + Spring AI + PostgreSQL + pgvector + Redis

类型的项目。

所以这次不希望完全复制相同技术栈。

我们讨论过：

PostgreSQL 是关系型数据库。

pgvector 是 PostgreSQL Extension。

所以不存在：

MySQL + pgvector

这种组合。

如果使用 pgvector，本质就是：

PostgreSQL + pgvector。

对于这个个人项目的实际数据规模来说：

**pgvector 本身其实完全够用。**

我并不是因为：

> “数据量超过百万，所以必须 Qdrant”

而选择 Qdrant。

个人知识库很可能只有几万～几十万 Vector，远达不到必须使用独立向量数据库的程度。

最终选择 Qdrant 的主要考虑是：

1. 已经实践过 pgvector；
2. 希望学习专用 Vector Database；
3. 学习业务数据库与 Vector Store 分离；
4. Qdrant Metadata Filtering 比较适合多知识域检索；
5. 后续有机会实践 Hybrid Search 等功能。

所以：

MySQL：

> Business Data

Qdrant：

> Vector Data

这是一种主动承担一点额外复杂度换取学习价值的选择，而不是性能刚需。

------

# 十五、为什么暂时不是 Milvus

Milvus 很强，但对于当前个人项目来说基础设施偏重。

我不希望项目最后主要精力都花在：

- 部署；
- MinIO；
- etcd；
- Vector Infrastructure；

这些事情上。

所以目前更倾向 Qdrant。

------

# 十六、Redis 的职责

Redis 暂时主要承担：

- Chat Memory
- Cache
- 临时状态

不希望 Redis 什么都做。

尤其不希望：

Redis 既 Cache、又 Memory、又把所有异步消息都放 Redis Streams。

所以异步消息准备单独交给 MQ。

------

# 十七、为什么目前选择 RabbitMQ

我们讨论过 RabbitMQ 和 RocketMQ。

目前这个项目的异步场景主要是：

- AI 回答后异步知识提取；
- 用户确认知识后异步进行 Chunk / Embedding / Index；
- 文件上传后的异步解析和索引。

这些场景更关注：

- ACK
- Retry
- DLQ
- 幂等
- 任务解耦

而不是：

- 超高吞吐；
- 大规模消息堆积；
- 强顺序消息；
- 大量事务消息。

所以目前倾向：

**RabbitMQ。**

不是因为 RocketMQ 不好，而是当前没有必要为了更强的消息能力增加复杂度。

------

# 十八、MQ 不应该进入实时聊天主链路

AI Chat 需要 SSE 流式输出。

所以不能设计：

用户
→ RabbitMQ
→ LLM
→ 用户

这种主链路。

RabbitMQ 只处理：

> 不需要阻塞用户实时回答的异步任务。

例如：

AI 回答完成
→ KnowledgeExtractEvent
→ RabbitMQ
→ Knowledge Extraction

用户确认知识
→ KnowledgeIndexEvent
→ RabbitMQ
→ Chunk
→ Embedding
→ Qdrant

------

# 十九、MySQL 和 Qdrant 的最终一致性

因为：

MySQL 和 Qdrant 是两个独立存储。

所以会产生：

> MySQL KnowledgeItem 保存成功，但是 Qdrant 索引失败怎么办？

目前不准备使用：

- Seata
- 2PC
- TCC

因为知识索引允许最终一致。

倾向通过：

- 状态
- RabbitMQ
- Retry
- DLQ
- 幂等

解决。

例如 Knowledge Processing 状态可能包含：

- PENDING
- PROCESSING
- INDEXED
- FAILED

具体状态模型需要后续再设计，不需要完全照搬这些名字。

------

# 二十、MQ 消费需要考虑幂等

因为消息可能重复消费。

例如 Knowledge Index Consumer 不能每消费一次就生成一套新的 Vector。

可能需要通过类似：

knowledgeItemId + chunkIndex

生成稳定 Vector ID，然后使用 Qdrant Upsert 等方式保证重复执行不会制造无限重复数据。

这也是后面希望重点学习的 Java 后端问题。

------

# 二十一、整体架构方向

目前倾向：

# 模块化单体

而不是微服务。

概念上：

Client
→ Spring Boot
→ MySQL / Redis / RabbitMQ / Qdrant
→ 外部 LLM Provider

Spring Boot 内部按照业务模块组织，例如可能包括：

- User / Auth
- Model Config
- Conversation
- Knowledge
- Knowledge Router
- RAG
- Document
- Knowledge Processing
- Async Task

具体模块拆分请后续重新分析，不需要机械照搬这些名字。

------

# 二十二、SSE

AI 回答主要是：

Server → Client

单向 Streaming。

所以 V1 倾向：

**SSE**

暂时没有必要为了聊天直接使用 WebSocket。

------

# 二十三、目前明确暂时不做 GraphRAG

我们专门讨论过 GraphRAG。

最后认为当前不应该加入。

原因：

普通个人知识库目前主要解决：

- Semantic Retrieval
- 局部知识问答
- 用户笔记检索

GraphRAG 会额外带来：

- Entity Extraction
- Relation Extraction
- Entity Resolution
- Graph Maintenance
- Incremental Update
- Graph Retrieval
- 更复杂评测

对于目前从 0 开发的基础版本来说明显过重。

所以目前：

**Vector RAG 优先。**

GraphRAG 只作为未来可能的研究方向，不提前为它设计复杂架构。

------

# 二十四、未来可能的扩展方向

下面这些是我未来比较感兴趣的 AI 能力：

- MCP
- Workflow
- Graph（工作流 Graph，不是 GraphRAG）
- Controlled Loop
- Agent
- Agent Skills
- Multi-Agent
- Hybrid Search
- Rerank
- Query Rewrite

但这些：

# 都不是当前 V1 必须实现的功能。

我希望系统首先把：

Chat

- Memory
- Personal Knowledge
- Vector RAG
- Knowledge Extraction

完整做通。

------

# 二十五、未来 MCP 的想法

以后可能让 KnowFlow：

## 作为 MCP Client

访问：

- 文件系统；
- Git / Repository；
- 其他外部 Tools / Resources。

这样 AI 可以结合：

> 外部代码 / 数据 + 我的个人知识库

进行回答。

## 作为 MCP Server

向：

- Codex
- Claude Code
- IDE Agent
- 其他 MCP Client

暴露个人知识能力。

例如未来可能提供：

- search_personal_knowledge
- list_knowledge_bases
- save_knowledge

这样我的个人知识库可以真正被其他 AI 工具使用。

但当前不实现。

------

# 二十六、未来 Workflow / Graph 的想法

当 AI Pipeline 越来越复杂以后，例如出现：

Question
→ Router
→ Retrieval
→ Evaluate
→ Query Rewrite
→ Retrieval
→ Rerank
→ Generate

如果继续靠大量 if / else 编排开始变得难维护，可以考虑：

Workflow / Graph。

这里的 Graph 指：

- State
- Node
- Edge
- Conditional Edge
- Loop

这和 GraphRAG 完全不是一回事。

但 V1 不要为了未来可能需要就提前引入 Workflow Engine。

------

# 二十七、未来 Agent 的想法

当前系统主要是：

> 用户问问题，系统回答问题。

这种场景不需要强行 Agent。

以后如果变成：

> 根据我以前的学习知识判断 Redis 哪些地方薄弱，帮我制定复习计划、出题、等待我回答、评分、再记录薄弱点。

这种“完成目标”的需求出现后，才考虑真正的：

Single Agent + Tools。

优先 Single Agent。

不要一开始就做 Multi-Agent。

------

# 二十八、未来 Agent Skills 的想法

我认为：

Knowledge：

> Agent 知道什么。

例如 Redis、JVM、Spring 知识。

Skill：

> Agent 知道应该怎么完成某类工作。

例如：

- Java Code Review Skill
- Interview Skill
- Knowledge Distillation Skill
- RAG Evaluation Skill

未来可能形成：

Agent

- Skills
- Knowledge
- Tools / MCP

但现在不实现。

------

# 二十九、Harness Engineering

这个和前面的产品功能不同。

因为我会大量使用 Codex 开发这个项目，所以我希望从项目早期逐渐建立：

**适合 AI 长期协作开发的工程环境。**

例如未来 Repo 中应该逐渐有：

- AGENTS.md
- architecture docs
- domain docs
- ADR
- development conventions
- scripts
- tests
- integration tests
- RAG eval dataset
- 明确的验证流程

目标不是让 Codex：

生成代码
→ 能编译
→ 完成。

而是逐渐做到：

任务
→ 阅读设计
→ 实现
→ Compile
→ Test
→ Integration Test
→ Review
→ 发现问题
→ Fix

也就是说：

Harness Engineering 应该从项目开发早期逐渐建设，而不是作为某个后期产品功能。

------

# 三十、目前最重要的项目原则

请以后始终遵守：

## 1. 不要为了技术而技术

每加入一个组件，都需要回答：

- 它解决什么真实问题？
- 不使用会有什么问题？
- 有没有更简单的方法？
- 当前阶段真的需要吗？

------

## 2. 不要过度设计

不要一开始：

- 拆微服务；
- 设计几十张表；
- 建大量 Base 类；
- 为所有 Service 提取 Interface；
- 上大量设计模式；
- 预留十几个未来扩展点。

优先解决当前真实业务。

------

## 3. 不要一次开发整个项目

后续应该：

设计
→ 小阶段开发
→ 运行验证
→ 理解代码
→ 再进入下一阶段。

------

## 4. 不要偷偷加入技术

如果你认为：

> 当前设计应该加入另一个框架 / 中间件。

请先说明：

- 为什么；
- 替代方案；
- 增加的复杂度；
- 带来的价值。

由我确认后再加入。

------

## 5. 不要只考虑“代码能跑”

需要同时考虑：

- 数据模型；
- 事务；
- 一致性；
- 异常；
- 幂等；
- 安全；
- 可观测；
- 测试；
- 可维护性。

但是这些也要按照项目阶段逐步加入，不要一次全部工业化。

------

# 三十一、我希望你现在做什么

现在请先把上面的内容当成：

# 项目需求与设计上下文

而不是已经完全确定的详细技术方案。

请先：

1. 阅读当前整个仓库；
2. 理解仓库现在是什么状态；
3. 根据上述产品目标重新梳理需求；
4. 判断当前设计有没有明显冲突或不合理；
5. 指出哪些内容已经足够确定；
6. 指出哪些地方应该在编码前进一步设计；
7. 基于真实依赖关系制定合理的开发阶段；
8. 给出推荐模块边界；
9. 给出数据库领域设计方向；
10. 给出开发顺序与每阶段验收标准；
11. 给出适合本项目的 AI 协作 / Harness 方案；
12. 标记 Future Considerations，而不是提前实现未来能力。

如果你认为我上面的某个设计存在明显问题，可以提出修改意见。

但请区分：

- 已确定需求；
- 推荐方案；
- 可选优化；
- Future Consideration。

不要把自己的所有建议直接当成确定需求。

------

# 三十二、本轮暂时不要做的事情

这一次先不要因为看到需求就直接开始生成整个项目。

尤其不要一次性：

- 创建所有数据库表；
- 实现所有业务模块；
- 实现 RAG；
- 实现 MQ；
- 实现 Agent；
- 实现 MCP；
- 添加大量依赖。

请先完成：

# 项目分析 + 架构延伸 + 开发规划

让我先确认你对项目的理解和规划。

确认以后，我们再开始真正进入编码阶段。

最后，请告诉我：

> 如果你作为这个项目的 Tech Lead，根据当前需求，你认为真正应该从哪个最小开发任务开始？