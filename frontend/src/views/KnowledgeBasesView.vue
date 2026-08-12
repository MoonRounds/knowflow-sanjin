<script setup lang="ts">
// 知识库管理页：列表 + 新建/编辑弹窗 + 启用/禁用/删除；从知识库创建 Manual Note；笔记列表可跳转详情。
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  disableKnowledgeBase,
  enableKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase,
} from '../api/knowledge-bases'
import type {
  CreateKnowledgeBaseRequest,
  KnowledgeBaseResponse,
  UpdateKnowledgeBaseRequest,
} from '../api/types/knowledge-base'
import { createKnowledgeItem, listKnowledgeItems } from '../api/knowledge-items'
import type { KnowledgeItemResponse } from '../api/types/knowledge-item'
import { uploadFile } from '../api/files'
import { errorText } from '../utils/errorText'
import KfEmptyState from '../components/KfEmptyState.vue'

const router = useRouter()

const loading = ref(false)
const items = ref<KnowledgeBaseResponse[]>([])
const notes = ref<KnowledgeItemResponse[]>([])

const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const form = reactive<{ name: string; description: string }>({
  name: '',
  description: '',
})

const noteDialogVisible = ref(false)
const noteForm = reactive<{
  title: string
  summary: string
  content: string
  knowledgeBaseIds: string[]
  tags: string
}>({ title: '', summary: '', content: '', knowledgeBaseIds: [], tags: '' })

const uploadDialogVisible = ref(false)
const uploadFileRaw = ref<File | null>(null)
const uploadKbIds = ref<string[]>([])
const uploading = ref(false)
const uploadProgress = ref(0)

const emptyState = ref(true)
/** 行级操作守卫：避免切换状态/删除重复提交。 */
const busyRowId = ref<string | null>(null)

async function load() {
  loading.value = true
  try {
    items.value = await listKnowledgeBases()
    emptyState.value = items.value.length === 0
    try {
      notes.value = await listKnowledgeItems()
    } catch {
      notes.value = []
    }
  } catch (e) {
    ElMessage.error(errorText(e, '加载知识库失败'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(row: KnowledgeBaseResponse) {
  editingId.value = row.id
  form.name = row.name
  form.description = row.description ?? ''
  dialogVisible.value = true
}

async function submit() {
  try {
    if (editingId.value === null) {
      const payload: CreateKnowledgeBaseRequest = {
        name: form.name.trim(),
        description: form.description,
      }
      await createKnowledgeBase(payload)
      ElMessage.success('知识库已创建')
    } else {
      const row = items.value.find((i) => i.id === editingId.value)
      if (!row) throw new Error('知识库已变化，请刷新后重试')
      const payload: UpdateKnowledgeBaseRequest = { rowVersion: row.rowVersion }
      if (form.name !== undefined) payload.name = form.name.trim()
      if (form.description !== undefined) payload.description = form.description
      await updateKnowledgeBase(editingId.value, payload)
      ElMessage.success('知识库已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '保存失败'))
  }
}

async function toggleEnabled(row: KnowledgeBaseResponse) {
  if (busyRowId.value) return
  busyRowId.value = row.id
  try {
    if (row.enabled) {
      await disableKnowledgeBase(row.id, row.rowVersion)
      ElMessage.info(`已禁用「${row.name}」`)
    } else {
      await enableKnowledgeBase(row.id, row.rowVersion)
      ElMessage.success(`已启用「${row.name}」`)
    }
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '切换状态失败'))
  } finally {
    busyRowId.value = null
  }
}

async function confirmDelete(row: KnowledgeBaseResponse) {
  if (busyRowId.value) return
  busyRowId.value = row.id
  try {
    await ElMessageBox.confirm(`确定删除知识库「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteKnowledgeBase(row.id, row.rowVersion)
    ElMessage.success('知识库已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(errorText(e, '删除失败'))
    }
  } finally {
    busyRowId.value = null
  }
}

function openCreateNote() {
  noteForm.title = ''
  noteForm.summary = ''
  noteForm.content = ''
  noteForm.tags = ''
  // 默认选中第一个知识库，避免提交时为空被后端拒绝
  noteForm.knowledgeBaseIds = items.value.length ? [items.value[0].id] : []
  noteDialogVisible.value = true
}

function openUpload() {
  uploadFileRaw.value = null
  uploadKbIds.value = items.value.length ? [items.value[0].id] : []
  uploadProgress.value = 0
  uploadDialogVisible.value = true
}

const MAX_FILE_BYTES = 5 * 1024 * 1024

function onUploadFileChange(file: File) {
  const name = file.name.toLowerCase()
  const okExt = name.endsWith('.md') || name.endsWith('.markdown') || name.endsWith('.txt')
  if (!okExt) {
    ElMessage.warning('仅支持 .md / .markdown / .txt 文件')
    uploadFileRaw.value = null
    return
  }
  if (file.size > MAX_FILE_BYTES) {
    ElMessage.warning('文件超过 5 MiB 上限')
    uploadFileRaw.value = null
    return
  }
  uploadFileRaw.value = file
}

async function submitUpload() {
  if (!uploadFileRaw.value) {
    ElMessage.warning('请选择文件')
    return
  }
  if (!uploadKbIds.value.length) {
    ElMessage.warning('请至少选择一个知识库')
    return
  }
  uploading.value = true
  uploadProgress.value = 10
  try {
    const result = await uploadFile(uploadFileRaw.value, uploadKbIds.value)
    uploadProgress.value = 100
    uploadDialogVisible.value = false
    await load()
    if (result.item?.id) {
      if (result.duplicate) {
        ElMessage.info('已存在相同内容的文件，跳转到已有条目')
        router.push(`/knowledge-items/${result.item.id}`)
      } else {
        ElMessage.success('文件已上传，正在解析与索引')
        router.push(`/knowledge-items/${result.item.id}`)
      }
    }
  } catch (e) {
    ElMessage.error(errorText(e, '上传失败'))
  } finally {
    uploading.value = false
  }
}

async function submitNote() {
  if (!noteForm.knowledgeBaseIds.length) {
    ElMessage.warning('请至少选择一个知识库')
    return
  }
  try {
    const created = await createKnowledgeItem({
      title: noteForm.title.trim() || undefined,
      summary: noteForm.summary,
      content: noteForm.content,
      knowledgeBaseIds: noteForm.knowledgeBaseIds,
      tags: noteForm.tags
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean),
    })
    ElMessage.success('笔记已创建，索引处理中')
    noteDialogVisible.value = false
    await load()
    router.push(`/knowledge-items/${created.id}`)
  } catch (e) {
    ElMessage.error(errorText(e, '创建笔记失败'))
  }
}

function noteStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'INDEXED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PROCESSING') return 'warning'
  return 'info'
}

onMounted(load)
</script>

<template>
  <div class="knowledge-bases-page kf-list-page">
    <div class="page-header kf-list-page-header">
      <h2>知识库</h2>
      <div class="kf-list-page-actions">
        <el-button type="primary" plain @click="openCreateNote"> 新建笔记 </el-button>
        <el-button type="primary" plain @click="openUpload"> 上传文件 </el-button>
        <el-button type="primary" @click="openCreate"> 新建知识库 </el-button>
      </div>
    </div>

    <KfEmptyState
      v-if="!loading && emptyState"
      title="给知识找一个长期生长的地方"
      description="先建立一个知识库，再把对话所得、手写笔记与上传文件沉淀进来。"
      action-label="新建知识库"
      wide
      @action="openCreate"
    />

    <el-table v-else v-loading="loading" :data="items" style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="description" label="描述" min-width="240">
        <template #default="{ row }">
          <span class="desc">{{ row.description || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '已启用' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">
          {{ new Date(row.updatedAt).toLocaleString() }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)"> 编辑 </el-button>
          <el-button
            size="small"
            :type="row.enabled ? 'warning' : 'success'"
            :loading="busyRowId === row.id"
            :disabled="busyRowId !== null && busyRowId !== row.id"
            @click="toggleEnabled(row)"
          >
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button
            size="small"
            type="danger"
            :loading="busyRowId === row.id"
            :disabled="busyRowId !== null && busyRowId !== row.id"
            @click="confirmDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3 v-if="loading || !emptyState" class="notes-heading">我的笔记</h3>
    <el-table v-if="loading || !emptyState" v-loading="loading" :data="notes" style="width: 100%">
      <el-table-column prop="title" label="标题" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="router.push(`/knowledge-items/${row.id}`)">
            {{ row.title }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="索引状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="noteStatusType(row.indexStatus)">
            {{ row.indexStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="contentVersion" label="版本" width="90">
        <template #default="{ row }">v{{ row.contentVersion }}</template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString() }}</template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      width="min(480px, 94vw)"
      align-center
      :show-close="false"
      class="kf-dialog"
    >
      <div class="kf-dialog-inner">
        <div class="kf-dialog-head">
          <div class="kf-dialog-tag">{{ editingId === null ? '新建' : '编辑' }}</div>
          <h3 class="kf-dialog-title">{{ editingId === null ? '新建知识库' : '编辑知识库' }}</h3>
          <p class="kf-dialog-sub">把知识放进一个分类，让 AI 之后能精准调用。</p>
          <button class="kf-dialog-close" aria-label="关闭" @click="dialogVisible = false">
            ✕
          </button>
        </div>
        <el-form class="kf-dialog-form" label-position="top">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" maxlength="200" placeholder="例如：前端工程化" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              maxlength="2000"
              placeholder="这个知识库装的是什么？用途说明（可选）"
            />
          </el-form-item>
        </el-form>
        <div class="kf-dialog-actions">
          <button class="kf-btn kf-btn-ghost" @click="dialogVisible = false">取消</button>
          <button class="kf-btn kf-btn-ink" :disabled="!form.name?.trim()" @click="submit">
            保存
          </button>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="uploadDialogVisible"
      title="上传 Markdown / TXT 文件"
      width="min(520px, 94vw)"
    >
      <el-form label-width="80px">
        <el-form-item label="文件" required>
          <el-upload
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept=".md,.markdown,.txt"
            :on-change="(uploadFile: any) => onUploadFileChange(uploadFile.raw)"
            :on-remove="() => (uploadFileRaw = null)"
          >
            <el-button> 选择文件 </el-button>
            <template #tip>
              <div class="upload-tip">支持 .md / .markdown / .txt，最大 5 MiB，UTF-8 编码。</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="知识库" required>
          <el-select
            v-model="uploadKbIds"
            multiple
            placeholder="选择知识库（至少一个）"
            style="width: 100%"
          >
            <el-option v-for="kb in items" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="uploading" label="进度">
          <el-progress :percentage="uploadProgress" :stroke-width="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false"> 取消 </el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!uploadFileRaw || !uploadKbIds.length"
          @click="submitUpload"
        >
          上传
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="noteDialogVisible" title="新建笔记" width="min(640px, 94vw)">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="noteForm.title" maxlength="500" placeholder="留空则取正文首行" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="noteForm.summary" type="textarea" :rows="2" maxlength="2000" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="noteForm.content"
            type="textarea"
            :rows="10"
            placeholder="Markdown 正文"
          />
        </el-form-item>
        <el-form-item label="知识库" required>
          <el-select
            v-model="noteForm.knowledgeBaseIds"
            multiple
            placeholder="选择知识库（至少一个）"
            style="width: 100%"
          >
            <el-option v-for="kb in items" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="noteForm.tags" placeholder="逗号分隔，如：ai, 笔记" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noteDialogVisible = false"> 取消 </el-button>
        <el-button type="primary" :disabled="!noteForm.content.trim()" @click="submitNote">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.knowledge-bases-page {
  width: 100%;
}
.page-header h2 {
  margin: 0;
  font-size: 1.25rem;
}
.desc {
  color: #666;
}
.upload-tip {
  color: #999;
  font-size: 0.8rem;
  margin-top: 4px;
}
.notes-heading {
  margin: 24px 0 12px;
  font-size: 1.1rem;
}
/* ---- 特色弹窗：纸感 + 硬阴影 ---- */
.kf-dialog-inner {
  padding: 22px 26px 24px;
}
.kf-dialog-head {
  position: relative;
  margin-bottom: 18px;
}
.kf-dialog-tag {
  display: inline-block;
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 0.08em;
  color: var(--kf-hot);
  border: 1px solid var(--kf-hot);
  border-radius: 999px;
  padding: 2px 9px;
  transform: rotate(-2deg);
}
.kf-dialog-title {
  font-size: 19px;
  font-weight: 900;
  letter-spacing: -0.4px;
  margin: 9px 0 4px;
}
.kf-dialog-sub {
  font-size: 11px;
  color: var(--kf-muted);
  font-weight: 600;
  margin: 0;
}
.kf-dialog-close {
  position: absolute;
  top: 0;
  right: 0;
  width: 30px;
  height: 30px;
  border: 1px solid var(--kf-line);
  border-radius: 10px;
  background: var(--kf-paper);
  color: var(--kf-muted);
  font-size: 12px;
  cursor: pointer;
}
.kf-dialog-close:hover {
  background: var(--kf-ink);
  color: var(--kf-paper);
}
.kf-dialog-form :deep(.el-input__wrapper),
.kf-dialog-form :deep(.el-textarea__inner) {
  border-radius: 10px;
}
.kf-dialog-form :deep(.el-form-item__label) {
  font-weight: 900;
  color: var(--kf-ink);
}
.kf-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
.kf-btn {
  min-height: 40px;
  padding: 0 18px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
  border: 1px solid var(--kf-ink);
  transition: 0.15s;
}
.kf-btn-ghost {
  background: transparent;
  color: var(--kf-ink);
}
.kf-btn-ghost:hover {
  background: var(--kf-paper-2);
}
.kf-btn-ink {
  background: var(--kf-ink);
  color: var(--kf-paper);
  box-shadow: 3px 3px 0 var(--kf-red);
}
.kf-btn-ink:hover:not(:disabled) {
  background: var(--kf-green);
  transform: translateY(-1px);
}
.kf-btn-ink:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  box-shadow: none;
}
.kf-btn:focus-visible {
  outline: var(--kf-focus-ring);
  outline-offset: 2px;
}
</style>

<style>
/* el-dialog 经 teleport 渲染到 body，scoped 样式不生效，故用全局块定制外壳。 */
.kf-dialog.el-dialog {
  border: 1px solid var(--kf-ink);
  border-radius: 18px;
  background: var(--kf-white);
  box-shadow: 8px 8px 0 var(--kf-red);
  padding: 0;
  overflow: hidden;
}
.kf-dialog .el-dialog__header,
.kf-dialog .el-dialog__footer {
  display: none;
}
</style>
