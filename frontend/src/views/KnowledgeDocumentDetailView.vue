<script setup lang="ts">
// Document 详情页：展示标题/摘要/正文/来源/所属知识库/标签/版本/索引状态；Manual Note 可编辑。
// 所属知识库名称通过前端 listKnowledgeBases() 映射（G27），库名链接回库详情。
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteDocument, getDocument, updateDocument } from '../api/documents'
import { listKnowledgeBases } from '../api/knowledge-bases'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'
import type { KnowledgeDocumentResponse } from '../api/types/document'
import { getFileMetadataByDocument, downloadFileUrl } from '../api/files'
import type { FileMetadataResponse } from '../api/files'
import { renderMarkdown } from '../utils/markdown'
import { useCodeBlockCopy } from '../composables/useCodeBlockCopy'
import { errorText } from '../utils/errorText'
import KfEmptyState from '../components/KfEmptyState.vue'

const route = useRoute()
const router = useRouter()

const itemId = String(route.params.id)
const loading = ref(true)
const saving = ref(false)
const item = ref<KnowledgeDocumentResponse | null>(null)
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])
const fileMeta = ref<FileMetadataResponse | null>(null)

const isUpload = computed(() => item.value?.sourceType === 'UPLOAD_FILE')

/** 所属知识库名称：由 knowledgeBaseId 在前端映射（G27）。 */
const knowledgeBaseName = computed(() => {
  const kbId = item.value?.knowledgeBaseId
  return knowledgeBases.value.find((kb) => kb.id === kbId)?.name ?? ''
})

/** 正文按受控 Markdown 渲染（raw HTML 已被 markdown-it 禁用）。 */
const renderedContent = computed(() => renderMarkdown(item.value?.content ?? ''))

const { handleClick: handleCodeBlockClick } = useCodeBlockCopy()

const editMode = ref(false)
const form = reactive<{
  title: string
  summary: string
  content: string
  knowledgeBaseId: string
  tags: string
}>({ title: '', summary: '', content: '', knowledgeBaseId: '', tags: '' })

let pollTimer: number | undefined

const indexStatus = computed(() => item.value?.indexStatus ?? 'PENDING')
const parseFailed = computed(() => isUpload.value && fileMeta.value?.parseStatus === 'FAILED')
const isPending = computed(
  () =>
    !parseFailed.value && (indexStatus.value === 'PENDING' || indexStatus.value === 'PROCESSING'),
)

async function load() {
  loading.value = true
  try {
    item.value = await getDocument(itemId)
    try {
      fileMeta.value = await getFileMetadataByDocument(itemId)
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
      const [latest, latestFile] = await Promise.all([
        getDocument(itemId),
        isUpload.value ? getFileMetadataByDocument(itemId) : Promise.resolve(null),
      ])
      item.value = latest
      if (latestFile) fileMeta.value = latestFile
      if (!isPending.value) {
        stopPolling()
      }
    } catch {
      // 轮询失败：先提示一次并停止，避免静默无限重试；用户可手动刷新页面
      stopPolling()
      ElMessage.error('索引状态刷新失败，请刷新页面重试')
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
  form.knowledgeBaseId = item.value.knowledgeBaseId
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
      knowledgeBaseId: form.knowledgeBaseId,
      tags: form.tags
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean),
      rowVersion: item.value.rowVersion,
    }
    const updated = await updateDocument(itemId, payload)
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
    await deleteDocument(itemId, item.value.rowVersion)
    ElMessage.success('笔记已删除')
    router.push('/knowledge-bases')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(errorText(e, '删除失败'))
    }
  }
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'INDEXED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

function indexStatusText(status: string): string {
  if (status === 'INDEXED') return '已索引'
  if (status === 'FAILED') return '索引失败'
  if (status === 'PROCESSING') return '索引中'
  if (status === 'PENDING') return '待索引'
  return status
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
        <div class="meta" aria-live="polite">
          <el-tag size="small" :type="statusTagType(item.indexStatus)">
            {{ indexStatusText(item.indexStatus) }}
          </el-tag>
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
            <el-tag size="small" :type="parseStatusType(fileMeta.parseStatus)">
              {{ fileMeta.parseStatus }}
            </el-tag>
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
        <div class="content markdown-body" @click="handleCodeBlockClick" v-html="renderedContent" />
        <el-divider />
        <div class="relations">
          <span class="label">所属知识库：</span>
          <el-link
            v-if="item.knowledgeBaseId"
            type="primary"
            @click="router.push(`/knowledge-bases/${item.knowledgeBaseId}`)"
          >
            {{ knowledgeBaseName || item.knowledgeBaseId }}
          </el-link>
          <span v-if="!item.knowledgeBaseId" class="muted">无</span>
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
            <el-select v-model="form.knowledgeBaseId" placeholder="选择知识库" style="width: 100%">
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

    <KfEmptyState
      v-else-if="!loading"
      title="这条知识已经不在这里了"
      description="它可能已被删除，或当前链接已经失效。返回知识库继续查看仍在生长的内容。"
      action-label="返回知识库"
      @action="router.push('/knowledge-bases')"
    />
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
  word-break: break-word;
  line-height: 1.7;
}
.content :deep(h1),
.content :deep(h2),
.content :deep(h3) {
  margin: 1em 0 0.5em;
  font-weight: 600;
}
.content :deep(p) {
  margin: 0.6em 0;
}
.content :deep(pre) {
  background: #f6f8fa;
  border-radius: 6px;
  padding: 10px 12px;
  overflow-x: auto;
}
.content :deep(code) {
  background: #f6f8fa;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 0.9em;
}
.content :deep(pre code) {
  background: transparent;
  padding: 0;
}
.content :deep(.kf-codeblock pre) {
  margin: 0;
  padding: 12px 14px;
  border-radius: 0;
  background: transparent;
}
.content :deep(blockquote) {
  border-left: 3px solid #e4e7ed;
  margin: 0.6em 0;
  padding-left: 12px;
  color: #888;
}
.content :deep(table) {
  border-collapse: collapse;
  margin: 0.8em 0;
}
.content :deep(th),
.content :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 6px 10px;
  font-size: 0.9em;
}
.content :deep(a) {
  color: #409eff;
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
