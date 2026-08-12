<script setup lang="ts">
// Processing 轻量页面：展示 PROCESSING 与 FAILED 任务，FAILED 显示错误摘要并可手动 Retry。
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProcessingTasks, retryProcessingTask } from '../api/processing-tasks'
import type { ProcessingTaskResponse } from '../api/types/processing-task'
import { errorText } from '../utils/errorText'
import KfEmptyState from '../components/KfEmptyState.vue'

const router = useRouter()

const loading = ref(false)
const items = ref<ProcessingTaskResponse[]>([])
const filter = ref<'PROCESSING' | 'FAILED'>('PROCESSING')
let timer: number | undefined
/** 行级重试守卫：避免重复提交。 */
const retryingId = ref<string | null>(null)

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
  if (retryingId.value) return
  retryingId.value = row.id
  try {
    await retryProcessingTask(row.id)
    ElMessage.success('已重新提交任务')
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '重试失败'))
  } finally {
    retryingId.value = null
  }
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

function goKnowledge() {
  void router.push('/knowledge-bases')
}

onMounted(async () => {
  await load()
  startAutoRefresh()
})

onBeforeUnmount(stopAutoRefresh)
</script>

<template>
  <div class="processing-page kf-list-page">
    <div class="page-header kf-list-page-header">
      <h2>处理任务</h2>
      <div class="header-controls kf-list-page-actions">
        <el-radio-group v-model="filter" @change="onFilterChange">
          <el-radio-button value="PROCESSING">处理中</el-radio-button>
          <el-radio-button value="FAILED">失败</el-radio-button>
        </el-radio-group>
        <el-button @click="load"> 刷新 </el-button>
      </div>
    </div>

    <div v-if="loading || items.length > 0" class="table-scroll" aria-live="polite">
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
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              size="small"
              type="primary"
              :loading="retryingId === row.id"
              :disabled="retryingId !== null && retryingId !== row.id"
              @click="retry(row)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <KfEmptyState
      v-if="!loading && items.length === 0"
      :title="filter === 'PROCESSING' ? '此刻没有正在处理的内容' : '没有需要恢复的失败任务'"
      description="从知识库创建笔记或上传文件后，解析与索引进度会在这里持续更新。"
      action-label="查看知识库"
      wide
      @action="goKnowledge"
    />
  </div>
</template>

<style scoped>
.processing-page {
  width: 100%;
}
.page-header h2 {
  margin: 0;
  font-size: 1.25rem;
}
.header-controls {
  display: flex;
  gap: 16px;
  align-items: center;
}
.table-scroll {
  overflow-x: auto;
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
