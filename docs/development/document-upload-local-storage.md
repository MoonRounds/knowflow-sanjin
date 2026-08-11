# 上传原文件本地存储说明

Phase 8 引入 Markdown/TXT 上传，原文件保存在本地对象式目录。本文说明目录布局、配置与运维注意点。

## 目录布局

默认存储根目录为 `knowflow-data/files`（相对后端启动目录），由
`knowflow.document.storage-root` 配置：

```text
knowflow-data/files/
├── <uuid>            # 正式原文件，存储键 = 随机 UUID（不含用户文件名）
└── tmp/              # 流式暂存临时文件，随请求清理
```

- 存储键由系统生成，杜绝路径穿越；用户文件名仅作展示与 Content-Disposition 编码。
- 删除 Item 时经 `UploadFileLifecycleHandler` 同步删除原文件并软删 FileMetadata。
- 数据库有记录但磁盘文件缺失时，下载返回「原文件缺失」可定位错误；重新上传相同内容会触发修复路径。

## 配置

| 配置项 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|
| `knowflow.document.storage-root` | `KNOWFLOW_DOCUMENT_STORAGE_ROOT` | `knowflow-data/files` | 本地存储根目录 |
| `knowflow.document.max-file-bytes` | `KNOWFLOW_DOCUMENT_MAX_FILE_BYTES` | `5242880`（5 MiB） | 单文件大小上限 |
| `knowflow.document.allowed-extensions` | `KNOWFLOW_DOCUMENT_ALLOWED_EXTENSIONS` | `md,markdown,txt` | 支持扩展名 |

## 持久化与备份

- `knowflow-data/` 已被 `.gitignore` 排除，不应进入版本库。
- 本地部署需将存储根目录挂载到宿主持久化磁盘；Docker Compose 通过 Volume 挂载
  `knowflow-data`（Phase 9 统一落实）。
- 原文件可随时从数据库 `file_metadata.storage_key` 反推定位；备份以目录级快照为主。

## 迁移与清理

- 未来迁移 MinIO 见 [ADR 0002](./docs/adr/0002-local-file-storage-for-upload.md)。
- 删除后的原文件立即从磁盘移除；软删 FileMetadata 行保留去重身份，重复上传时触发恢复。
