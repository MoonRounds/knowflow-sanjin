# ADR 0002：上传原文件本地对象式存储（未来迁移 MinIO）

- 状态：已接受
- 日期：2026-08-11
- 决策人：项目 Owner

## 背景

Phase 8 引入 Markdown/TXT 上传。原文件需要持久化保存、支持安全下载，并满足去重、删除与恢复语义。
引入完整对象存储服务（MinIO）在本阶段会带来额外基础设施与运维负担，而 V1 是单用户、本地优先部署。

## 决策

- V1 原文件保存到受控本地目录（`knowflow.document.storage-root`，默认 `knowflow-data/files`），
  存储键由系统生成（随机 UUID），用户文件名不参与路径拼接。
- 使用 `tika-core` 做 MIME/文本检测，`commonmark-java` 做 Markdown AST 解析，不引入完整 Tika Parsers。
- 文件正式落盘与数据库提交在同一事务边界；提交失败由补偿删除或孤儿回收兜底。
- 下载经 owner 校验的后端接口，Content-Disposition 正确转义，不暴露真实路径或静态目录直出。
- 去重身份为 `ownerId + 检测 MIME + 原始内容 SHA-256`，文件名不参与唯一键。

## 面向未来 MinIO 迁移

- 不在本阶段引入 MinIO 客户端或存储抽象层；`FileStorageService` 保持薄封装。
- 迁移时将本地目录实现替换为 MinIO 的 `putObject/getObject/deleteObject`，保持
  `FileStorageService` 与 `LocalFileStore` 的调用点不变，数据库 `storage_key` 语义沿用。
- 迁移需配套：存量文件批量上传、删除语义对齐、下载鉴权边界不变。

## 影响

- 本地部署零额外依赖；原文件安全性依赖本地目录权限与 `knowflow-data` 被 `.gitignore` 排除。
- Docker Compose 需为 `knowflow-data` 挂载持久化 Volume（Phase 9 统一落实）。
