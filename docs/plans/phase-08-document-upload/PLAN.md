# Phase 08：Markdown/TXT 上传、去重与文档处理

## 1. 阶段目标

让用户从 KnowledgeBase 页面上传 Markdown/TXT 原文件。系统先安全接收并基于 MIME 与原始内容哈希完成去重，再异步解析为一个对应 KnowledgeItem，最后复用现有 Chunk、Embedding 和 Qdrant 索引链路。

V1 原文件先使用本地对象存储式目录保存；MinIO 是后续明确迁移目标，但本阶段不引入 MinIO 依赖或抽象过度的存储框架。

## 2. 开始条件

- Phase 07 已完成并通过独立 Review；
- KnowledgeItem、ProcessingTask 和异步索引链路稳定；
- KnowledgeBase 页面已有创建知识入口；
- 应用本地数据目录和 Docker Compose 持久化边界已明确。

## 3. 本阶段范围

### 3.1 上传与原文件

- 支持 `.md`、`.markdown`、`.txt`；
- 单文件大小上限默认 5 MiB，配置化；
- 一个请求上传一个文件；
- 每个文件创建一个 FileMetadata 和一个对应 KnowledgeItem；
- 原文件保存到受控本地存储目录，存储键由系统生成；
- 原始文件名仅作展示，不参与路径拼接；
- 提供经过 owner 校验的安全下载；
- 不通过静态目录直接暴露原文件。

### 3.2 校验与去重顺序

去重必须发生在正式解析和创建新业务对象之前：

1. 流式接收至临时文件，同时计算原始内容 SHA-256 和大小；
2. 根据扩展名、声明 Content-Type、Apache Tika 检测 MIME 和基础文本有效性进行校验；
3. 以 `ownerId + normalizedDetectedMimeType + rawContentSha256` 作为重复身份；
4. 查询并发安全的唯一约束；
5. 若存在活动文件，返回已存在的 FileMetadata/KnowledgeItem，不重复创建和索引；
6. 若命中已软删除文件，采用既定的恢复语义，不悄悄制造重复对象；
7. 仅对新文件移动到正式存储并创建 FileMetadata、KnowledgeItem 和文档处理任务。

文件名不参与去重。原始内容哈希在解析前计算，避免解析规范化掩盖文件差异，也避免为重复文件重复执行昂贵解析。

### 3.3 Apache Tika 与解析

- 只引入满足 Markdown/TXT 检测需求的 Apache Tika Core；
- 不引入完整 `tika-parsers-standard-package`；
- Tika 用于 MIME 检测和基础内容校验，不承担 Markdown 结构解析；
- Markdown 使用 `commonmark-java` AST 解析；
- TXT 按 UTF-8 读取；
- V1 只接受有效 UTF-8 文本；
- 规范正文统一移除 UTF-8 BOM、规范换行为 LF，并保持可解释的文本结构；
- Markdown 的链接文字、代码块、标题和列表应合理保留为可检索文本；
- 不执行 Markdown 中的 HTML/脚本。

### 3.4 Item 内容映射

- 文件解析正文是 KnowledgeItem 的规范 content；
- Markdown 优先使用第一个 H1 作为 title，否则使用安全化原文件名；
- TXT 使用安全化原文件名作为 title；
- V1 不调用 AI 自动生成 summary；
- `sourceType = UPLOAD_FILE`；
- Upload Item 正文在 V1 前端只读；
- FileMetadata 与 KnowledgeItem 一对一；
- 一个 Item 再拆成多个 Chunk。

### 3.5 异步处理边界

- 上传请求只负责安全落盘、去重、创建元数据/Item 和提交 Document Processing Task；
- 文档解析异步执行；
- 解析成功后再提交独立的 Knowledge Index Task；
- 文档处理失败与索引失败是两个可区分任务；
- 两者均使用既有 PENDING/PROCESSING/SUCCEEDED/FAILED、Retry 与 DLQ；
- 解析重复消费不得重复创建 Item 或重复提交无限索引任务。

### 3.6 前端

- Upload 是 KnowledgeBase 的 Dialog、Drawer 或子流程，不是一级导航；
- 选择文件、显示格式/大小约束和上传进度；
- 重复文件返回已有 Item，并给出清楚提示；
- 上传成功后进入 KnowledgeItem Detail；
- 展示文件名、MIME、大小、SHA 摘要、处理状态、索引状态；
- 原文件可安全下载；
- Upload Item 正文只读；
- 文档或索引失败可从 Item/Processing Retry。

## 4. 明确不做

- PDF、Word；
- 非 UTF-8 文本自动猜测与转码；
- 多文件批量上传；
- 文件夹上传；
- OCR；
- MinIO 接入；
- 完整 Apache Tika parser 包；
- AI 自动摘要；
- Upload Item 在线编辑；
- 复杂相似文件/语义去重；
- 防病毒产品集成。

## 5. 安全与一致性约束

- 校验服务端实际读取字节，不能信任扩展名或请求 Content-Type；
- 拒绝二进制伪装文本、空文件、超限文件和非法 UTF-8；
- 临时文件无论成功或失败都必须清理；
- 路径不得包含用户文件名；
- 下载 Content-Disposition 正确转义；
- 错误和日志不输出完整私人正文；
- 数据库唯一约束处理并发重复上传；
- 文件正式落盘与数据库提交失败之间必须有补偿清理或孤儿回收策略；
- 数据库已有记录但文件丢失时必须呈现可定位失败，而不是空正文；
- 删除语义同时考虑 FileMetadata、KnowledgeItem、原文件和 Qdrant 索引。

## 6. 实施步骤

### Step A：FileMetadata 与本地存储

- migration 与 owner 隔离；
- 配置化存储根目录；
- 安全 storage key；
- 临时文件、原子移动和清理；
- owner 校验下载；
- 路径穿越和文件名转义测试。

### Step B：流式校验与内容去重

- 上传大小限制；
- 流式 SHA-256；
- Tika MIME 检测；
- 扩展名、声明 MIME 与检测 MIME 的明确兼容规则；
- UTF-8/文本校验；
- 数据库唯一约束；
- 活动重复返回与软删除恢复；
- 并发重复上传测试。

### Step C：异步解析

- Document Processing Task；
- TXT 规范化；
- Markdown commonmark AST 解析；
- title 推导；
- 保存 Item 规范正文；
- 解析成功后可靠提交 Index Task；
- 重复消费和失败 Retry。

### Step D：索引复用与生命周期

- 复用 Phase 05 Chunk/Embedding/Qdrant；
- sourceType/file metadata 进入必要 payload；
- 删除、恢复与重新索引；
- 文件丢失、解析失败、索引失败状态可区分。

### Step E：前端上传闭环

- KnowledgeBase 内上传入口；
- 限制说明、进度、重复提示；
- Item Detail 文件元数据与只读正文；
- 下载；
- Document/Index 任务错误和 Retry。

## 7. 测试要求

至少覆盖：

- 合法 Markdown/TXT；
- `.md`/`.txt` 与 MIME 映射；
- 扩展名伪装、错误请求 Content-Type；
- 二进制内容伪装文本；
- 空文件、超过 5 MiB；
- UTF-8 BOM 与 CRLF 规范化；
- 非 UTF-8 拒绝；
- Markdown H1 和无 H1 title；
- 代码块、列表、链接文本解析；
- 文件名路径穿越与 Content-Disposition 注入；
- 相同内容不同文件名去重；
- 相同文件名不同内容不去重；
- 相同内容不同标准 MIME 规则；
- 并发上传同一文件只产生一个活动 FileMetadata/Item；
- 软删除后的重复上传语义；
- 数据库失败、文件移动失败和临时文件清理；
- Parser 可重试/不可重试失败；
- Parser 重复消费；
- 解析成功后 Index Task 只可靠提交一次；
- owner 越权下载；
- 删除后原文件与索引生命周期。

## 8. 阶段验收

通过前端完成：

1. 在 KnowledgeBase 中上传一个 Markdown 文件；
2. 看到 FileMetadata 和一个对应 KnowledgeItem；
3. 文档解析成功，Item 展示规范正文且只读；
4. 索引成功；
5. 新建 Conversation 并通过自动 RAG 找到该文件知识；
6. Sources 展示 Item 与文件来源，可进入详情；
7. 安全下载原文件；
8. 更名后上传相同字节文件，系统返回已有 Item；
9. 使用同名但不同内容文件，系统创建新 Item；
10. 注入解析失败，看到 Document Processing `FAILED` 并可 Retry；
11. 注入索引失败，确认它与解析失败清楚区分。

## 9. 阶段交付物

- FileMetadata 与本地原文件存储；
- 上传、校验、去重和安全下载 API；
- Tika Core MIME 检测与 commonmark-java 解析；
- Document Processing 异步链路；
- KnowledgeBase 上传入口与 Item 文件展示；
- 集成测试与本地存储运行说明；
- 面向未来 MinIO 迁移的 ADR 说明，但不实现迁移层。

## 10. 完成后动作

1. 运行快速、集成和固定 Eval；
2. 运行上传安全与去重验收样例；
3. 提交本阶段代码；
4. 在全新 Codex 任务中依据 [REVIEW.md](./REVIEW.md) 独立 Review；
5. Review 通过后再进入 Phase 09。
