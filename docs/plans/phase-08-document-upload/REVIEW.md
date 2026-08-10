# Phase 08 Review：Markdown/TXT 上传、去重与文档处理

## 1. Review 目标

确认文件上传不会因不可信文件名/MIME、并发重复、失败清理或异步重投产生安全漏洞和重复知识，并确认一个文件严格映射一个 FileMetadata 与一个 KnowledgeItem。

## 2. Review 前置输入

- [全局决策基线](../DECISIONS.md)
- [Phase 08 Plan](./PLAN.md)
- Phase 08 起止提交；
- 文件类型规则、去重键和本地存储说明；
- 自动化及手工安全验收结果。

## 3. 必查项

### 3.1 上传安全

- 是否流式限制大小，而非读入内存后才判断；
- 是否信任了扩展名或请求 Content-Type；
- Tika 是否只引入必要 Core；
- 是否能拒绝二进制伪装文本、非法 UTF-8 和空文件；
- 用户文件名是否进入磁盘路径；
- 下载是否有 owner 校验和安全 Content-Disposition；
- 是否从静态目录直接公开私人文件。

### 3.2 去重

- SHA-256 是否基于解析前原始字节；
- key 是否包含 owner + 标准检测 MIME + hash；
- 文件名是否错误参与去重；
- 数据库是否有并发唯一约束；
- 重复请求是否返回同一 Item；
- 软删除后的语义是否明确；
- 同名不同内容是否被错误合并。

### 3.3 文件与数据库一致性

- 临时文件在所有失败路径是否清理；
- 正式文件移动成功但数据库失败是否产生不可控孤儿；
- 数据库记录存在但文件丢失是否可诊断；
- 删除是否同时处理 FileMetadata、Item、原文件与索引；
- 是否为未来 MinIO 过度设计复杂 Adapter。

### 3.4 解析与内容

- Markdown 是否使用 commonmark AST，而非脆弱正则；
- Tika 是否被误用为结构解析器；
- BOM、LF 和 UTF-8 规范化是否确定；
- title 规则是否符合 H1/文件名约定；
- 是否错误调用 AI 生成 summary；
- Upload Item 是否只读；
- Markdown 中 HTML/脚本是否可能执行。

### 3.5 异步与幂等

- 上传请求是否避免同步做 Parser/Embedding/Qdrant；
- Document Task 与 Index Task 是否可区分；
- Parser 重复消费是否会重复创建 Item/Index Task；
- 解析成功与提交索引任务之间是否有可靠消息边界；
- FAILED、Retry 和 DLQ 是否沿用统一规则。

### 3.6 前端产品边界

- Upload 是否从 Knowledge 页面进入，而非新增一级导航；
- 重复文件提示是否指向已有 Item；
- 文档失败与索引失败是否区分；
- 是否显示必要元数据但不暴露本地真实路径；
- 来源是否能从 Chat 追溯到 FileMetadata/Item。

## 4. 必跑验证

- `scripts/verify-fast.sh`；
- `scripts/verify-integration.sh`；
- 路径穿越和响应头注入测试；
- MIME spoof、二进制和非法 UTF-8 测试；
- 大文件流式拒绝测试；
- 同内容不同名、同名不同内容测试；
- 并发重复上传测试；
- 临时文件和孤儿清理测试；
- Parser 失败/Retry/重复消费测试；
- 解析到索引可靠提交测试；
- owner 越权下载测试；
- 完整上传 → 索引 → RAG → 来源 E2E。

## 5. 高风险反例

- 使用 `originalFilename` 拼接存储路径；
- 仅看 `.md` 后缀就接收二进制；
- 先完整解析，再计算哈希去重；
- 仅用文件名去重；
- 用应用层“先查后插”而无唯一约束；
- 解析重试创建第二个 KnowledgeItem；
- 上传 Controller 同步调用 Embedding；
- 删除数据库记录却保留可公开下载的原文件；
- 引入完整 Tika parsers 导致无必要依赖面；
- 为未来 MinIO 建设庞大存储抽象。

## 6. Review 输出格式

按 P0/P1/P2/P3 输出 findings，给出证据、复现、安全或数据影响、违反基线、最小修复和缺失测试。

结尾分别给出 Spec、Standards、安全性、去重/一致性可信度和是否允许进入 Phase 09。
