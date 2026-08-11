<script setup lang="ts">
// KnowledgeItem 详情页：展示标题/摘要/正文/来源/知识库/标签/版本/索引状态；Manual Note 可编辑。
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ApiError,
  deleteKnowledgeItem,
  getKnowledgeItem,
  updateKnowledgeItem,
} from '../api/knowledge-items'
import { listKnowledgeBases } from '../api/knowledge-bases'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'
import type { KnowledgeItemResponse } from '../api/types/knowledge-item'
import { getFileMetadataByItem, downloadFileUrl } from '../api/files'
import type { FileMetadataResponse } from '../api/files'

const route = useRoute()
const router = useRouter()

const itemId = String(route.params.id)
const loading = ref(true)
const saving = ref(false)
const item = ref<KnowledgeItemResponse | null>(null)
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const fileMeta = ref<FileMetadataResponse | null>(null)

const isUpload = computed(() => item.value?.sourceType === 'UPLOAD_FILE')

const editMode = ref(false)
const form = reactive<{
  title: string
  summary: string
  content: string
  knowledgeBaseIds: string[]
  tags: string
}>({ title: '', summary: '', content: '', knowledgeBaseIds: [], tags: '' })

let pollTimer: number | undefined

const indexStatus = computed(() => item.value?.indexStatus ?? 'PENDING')
const isPending = computed(
  () => indexStatus.value === 'PENDING' || indexStatus.value === 'PROCESSING',
)

async function load() {
  loading.value = true
  try {
    item.value = await getKnowledgeItem(itemId)
    try {
      fileMeta.value = await getFileMetadataByItem(itemId)
    } catch {
      fileMeta.value = null
    }
    if (isPending.value) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch (e) {
    ElMessage.error(errorText(e, '加载笔记失败'))
  } finally {
    loading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const latest = await getKnowledgeItem(itemId)
      item.value = latest
      if (!isPending.value) {
        stopPolling()
      }
    } catch {
      stopPolling()
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

function enterEdit() {
  if (!item.value) return
  editMode.value = true
  form.title = item.value.title
  form.summary = item.value.summary ?? ''
  form.content = item.value.content
  form.knowledgeBaseIds = [...item.value.knowledgeBaseIds]
  form.tags = item.value.tags.join(', ')
}

async function save() {
  if (!item.value) return
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      summary: form.summary,
      content: form.content,
      knowledgeBaseIds: form.knowledgeBaseIds,
      tags: form.tags
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean),
      rowVersion: item.value.rowVersion,
    }
    const updated = await updateKnowledgeItem(itemId, payload)
    item.value = updated
    editMode.value = false
    ElMessage.success('笔记已保存')
    if (updated.indexStatus === 'PENDING' || updated.indexStatus === 'PROCESSING') {
      startPolling()
    }
  } catch (e) {
    ElMessage.error(errorText(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function confirmDelete() {
  if (!item.value) return
  try {
    await ElMessageBox.confirm('确定删除这条笔记吗？删除后知识将不再可检索。', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteKnowledgeItem(itemId, item.value.rowVersion)
    ElMessage.success('笔记已删除')
    router.push('/knowledge-bases')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(errorText(e, '删除失败'))
    }
  }
}

function errorText(e: unknown, fallback: string): string {
  if (e instanceof ApiError) return e.message || e.errorCode || fallback
  return e instanceof Error ? e.message : fallback
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'INDEXED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

function parseStatusType(status: string | undefined): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function downloadFile() {
  if (!fileMeta.value?.id) return
  window.open(downloadFileUrl(fileMeta.value.id), '_blank')
}

onMounted(async () => {
  try {
    knowledgeBases.value = await listKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  }
  await load()
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <div v-loading="loading" class="item-page">
    <template v-if="item">
      <div class="page-header">
        <el-button link @click="router.push('/knowledge-bases')">← 返回知识库</el-button>
        <el-button v-if="!editMode && !isUpload" type="primary" @click="enterEdit">
          编辑
        </el-button>
        <el-button v-if="!editMode" type="danger" plain @click="confirmDelete"> 删除 </el-button>
      </div>

      <div v-if="!editMode" class="view-mode">
        <h2>{{ item.title }}</h2>
        <div class="meta">
          <el-tag size="small" :type="statusTagType(item.indexStatus)">{{
            item.indexStatus
          }}</el-tag>
          <span class="meta-item">内容版本 v{{ item.contentVersion }}</span>
          <span v-if="item.indexedVersion != null" class="meta-item">
            已索引 v{{ item.indexedVersion }}
          </span>
          <span class="meta-item">来源：{{ item.sourceType }}</span>
        </div>
        <div v-if="fileMeta" class="file-box">
          <div class="file-row">
            <span class="label">文件：</span>
            <span class="file-name">{{ fileMeta.originalFilename }}</span>
            <el-tag size="small">{{ fileMeta.detectedMimeType }}</el-tag>
            <span class="meta-item">{{ formatBytes(fileMeta.byteSize ?? 0) }}</span>
            <span class="meta-item">SHA-256 {{ (fileMeta.sha256 ?? '').slice(0, 12) }}…</span>
          </div>
          <div class="file-row">
            <span class="label">解析状态：</span>
            <el-tag size="small" :type="parseStatusType(fileMeta.parseStatus)">{{
              fileMeta.parseStatus
            }}</el-tag>
            <el-button size="small" link @click="downloadFile"> 下载原文件 </el-button>
          </div>
          <div v-if="fileMeta.parseStatus === 'FAILED'" class="error-box">
            <strong>解析失败：</strong>
            {{ fileMeta.parseErrorCode ?? 'UNKNOWN' }}
            <span v-if="fileMeta.parseErrorMessage"> — {{ fileMeta.parseErrorMessage }} </span>
            <el-button size="small" type="danger" link @click="router.push('/processing')">
              前往任务页重试
            </el-button>
          </div>
        </div>
        <div v-if="item.indexStatus === 'FAILED'" class="error-box">
          <strong>索引失败：</strong>
          {{ item.indexErrorCode ?? 'UNKNOWN' }}
          <span v-if="item.indexErrorMessage"> — {{ item.indexErrorMessage }}</span>
        </div>
        <div v-if="item.summary" class="summary">{{ item.summary }}</div>
        <el-divider />
        <pre class="content">{{ item.content }}</pre>
        <el-divider />
        <div class="relations">
          <span class="label">知识库：</span>
          <el-tag v-for="kb in item.knowledgeBaseIds" :key="kb" size="small" type="primary">
            {{ kb }}
          </el-tag>
          <span v-if="!item.knowledgeBaseIds.length" class="muted">无</span>
        </div>
        <div class="relations">
          <span class="label">标签：</span>
          <el-tag v-for="tag in item.tags" :key="tag" size="small">{{ tag }}</el-tag>
          <span v-if="!item.tags.length" class="muted">无</span>
        </div>
      </div>

      <div v-else class="edit-mode">
        <el-form label-width="80px">
          <el-form-item label="标题" required>
            <el-input v-model="form.title" maxlength="500" placeholder="笔记标题" />
          </el-form-item>
          <el-form-item label="摘要">
            <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="2000" />
          </el-form-item>
          <el-form-item label="正文" required>
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="12"
              placeholder="Markdown 正文"
            />
          </el-form-item>
          <el-form-item label="知识库" required>
            <el-select
              v-model="form.knowledgeBaseIds"
              multiple
              placeholder="选择知识库（至少一个）"
              style="width: 100%"
            >
              <el-option
                v-for="kb in knowledgeBases"
                :key="kb.id"
                :label="kb.name"
                :value="kb.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="form.tags" placeholder="逗号分隔，如：ai, 笔记" />
          </el-form-item>
        </el-form>
        <div class="edit-actions">
          <el-button @click="editMode = false"> 取消 </el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!form.content.trim()"
            @click="save"
          >
            保存
          </el-button>
        </div>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="笔记不存在或已被删除" />
  </div>
</template>

<style scoped>
.item-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.view-mode h2 {
  margin: 8px 0;
  font-size: 1.4rem;
}
.meta {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
  font-size: 0.85rem;
  color: #888;
}
.error-box {
  background: #fef0f0;
  color: #c45656;
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 12px;
}
.file-box {
  background: #f6f8fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 0.85rem;
}
.file-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 4px;
}
.file-name {
  font-weight: 500;
}
.meta-item {
  color: #888;
}
.summary {
  color: #555;
  font-style: italic;
  margin-bottom: 8px;
}
.content {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.6;
}
.relations {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}
.label {
  color: #666;
  font-size: 0.85rem;
}
.muted {
  color: #bbb;
  font-size: 0.85rem;
}
.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
