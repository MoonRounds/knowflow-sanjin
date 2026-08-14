// SourceType 枚举 → 中文文案：来源面板标签、文档列表来源列/过滤下拉共享，避免各组件重复映射。
export const SOURCE_TYPE_TEXT: Record<string, string> = {
  MANUAL_NOTE: '笔记',
  UPLOAD_FILE: '上传文件',
  AI_CONVERSATION: '对话沉淀',
}

export function sourceTypeLabel(value?: string | null): string {
  if (!value) return ''
  return SOURCE_TYPE_TEXT[value] ?? value
}

/** 过滤下拉选项（调用方自行前插「全部」空选项）。 */
export const SOURCE_TYPE_OPTIONS = Object.entries(SOURCE_TYPE_TEXT).map(([value, label]) => ({
  value,
  label,
}))
