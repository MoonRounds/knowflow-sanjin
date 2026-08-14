<script setup lang="ts">
// 文档列表状态列（G34）：上传文件先看解析状态（FAILED/PENDING→解析中），解析成功后按索引状态展示。
// 独立组件缓存 displayStatus 计算，避免父级模板对同一 row 多次调用。
import { computed } from 'vue'
import type { KnowledgeDocumentSummaryResponse } from '../api/types/document'
import { formatErrorText } from '../utils/errorText'

const props = defineProps<{ row: KnowledgeDocumentSummaryResponse }>()

const STATUS_LABELS: Record<string, string> = {
  PENDING: '待索引',
  PROCESSING: '索引中',
  INDEXED: '已索引',
  FAILED: '索引失败',
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'INDEXED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

const display = computed(() => {
  const row = props.row
  const parse = row.parseStatus
  if (parse && parse !== 'SUCCEEDED') {
    if (parse === 'FAILED') {
      return {
        label: '解析失败',
        type: 'danger' as const,
        errorText: formatErrorText(row.parseErrorCode, row.parseErrorMessage),
      }
    }
    return { label: '解析中', type: 'warning' as const }
  }
  if (row.indexStatus === 'FAILED') {
    return {
      label: '索引失败',
      type: 'danger' as const,
      errorText: formatErrorText(row.indexErrorCode, row.indexErrorMessage),
    }
  }
  return {
    label: STATUS_LABELS[row.indexStatus] ?? row.indexStatus,
    type: statusType(row.indexStatus),
  }
})
</script>

<template>
  <el-tooltip v-if="display.errorText" :content="display.errorText" placement="top">
    <el-tag size="small" :type="display.type">{{ display.label }}</el-tag>
  </el-tooltip>
  <el-tag v-else size="small" :type="display.type">{{ display.label }}</el-tag>
</template>
