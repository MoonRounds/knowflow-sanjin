<script setup lang="ts">
// Processing 轻量页面：展示 PROCESSING 与 FAILED 任务，FAILED 显示错误摘要并可手动 Retry。
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ApiError, listProcessingTasks, retryProcessingTask } from '../api/processing-tasks'
import type { ProcessingTaskResponse } from '../api/types/processing-task'

const loading = ref(false)
const items = ref<ProcessingTaskResponse[]>([])
const filter = ref<'PROCESSING' | 'FAILED'>('PROCESSING')
let timer: number | undefined

async function load() {
  loading.value = true
  try {
    items.value = await listProcessingTasks(filter.value)
  } catch (e) {
    ElMessage.error(errorText(e, '加载任务失败'))
  } finally {
    loading.value = false
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  timer = setInterval(load, 10_000)
}

function stopAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
}

async function retry(row: ProcessingTaskResponse) {
  try {
    await retryProcessingTask(row.id)
    ElMessage.success('已重新提交任务')
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '重试失败'))
  }
}

function errorText(e: unknown, fallback: string): string {
  if (e instanceof ApiError) return e.message || e.errorCode || fallback
  return e instanceof Error ? e.message : fallback
}

function statusType(status: string): 'warning' | 'danger' | 'success' {
  if (status === 'FAILED') return 'danger'
  if (status === 'SUCCEEDED') return 'success'
  return 'warning'
}

async function onFilterChange() {
  await load()
  startAutoRefresh()
}

onMounted(async () => {
  await load()
  startAutoRefresh()
})

onBeforeUnmount(stopAutoRefresh)
</script>

<template>
  <div class="processing-page">
    <div class="page-header">
      <h2>处理任务</h2>
      <div class="header-controls">
        <el-radio-group v-model="filter" @change="onFilterChange">
          <el-radio-button value="PROCESSING">处理中</el-radio-button>
          <el-radio-button value="FAILED">失败</el-radio-button>
        </el-radio-group>
        <el-button @click="load"> 刷新 </el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="items" style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="taskType" label="类型" width="140" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="重试" width="90">
        <template #default="{ row }">{{ row.retryCount }}/{{ row.maxRetries }}</template>
      </el-table-column>
      <el-table-column prop="businessKey" label="业务键" min-width="200" />
      <el-table-column label="错误" min-width="220">
        <template #default="{ row }">
          <span v-if="row.failureCode" class="err">{{ row.failureCode }}</span>
          <span v-if="row.lastError" class="err-detail" :title="row.lastError">
            {{ row.lastError.length > 80 ? row.lastError.slice(0, 80) + '…' : row.lastError }}
          </span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170">
        <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'FAILED'" size="small" type="primary" @click="retry(row)">
            Retry
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && items.length === 0" description="暂无此类任务" />
  </div>
</template>

<style scoped>
.processing-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 1.25rem;
}
.header-controls {
  display: flex;
  gap: 8px;
  align-items: center;
}
.err {
  color: #c45656;
  font-weight: 600;
  margin-right: 6px;
}
.err-detail {
  color: #a55;
  font-size: 0.85rem;
}
.muted {
  color: #bbb;
}
</style>
