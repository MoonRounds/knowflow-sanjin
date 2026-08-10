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
  ApiError,
} from '../api/knowledge-bases'
import type {
  CreateKnowledgeBaseRequest,
  KnowledgeBaseResponse,
  UpdateKnowledgeBaseRequest,
} from '../api/types/knowledge-base'
import { createKnowledgeItem, listKnowledgeItems } from '../api/knowledge-items'
import type { KnowledgeItemResponse } from '../api/types/knowledge-item'

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

const emptyState = ref(true)

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
  }
}

async function confirmDelete(row: KnowledgeBaseResponse) {
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

function errorText(e: unknown, fallback: string): string {
  if (e instanceof ApiError) return e.message || e.errorCode || fallback
  return e instanceof Error ? e.message : fallback
}

onMounted(load)
</script>

<template>
  <div class="knowledge-bases-page">
    <div class="page-header">
      <h2>知识库</h2>
      <div>
        <el-button type="primary" plain @click="openCreateNote"> 新建笔记 </el-button>
        <el-button type="primary" @click="openCreate"> 新建知识库 </el-button>
      </div>
    </div>

    <el-empty v-if="!loading && emptyState" description="还没有知识库，点击右上角创建第一个" />

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
            @click="toggleEnabled(row)"
          >
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" @click="confirmDelete(row)"> 删除 </el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3 class="notes-heading">我的笔记</h3>
    <el-table v-loading="loading" :data="notes" style="width: 100%">
      <el-table-column prop="title" label="标题" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="router.push(`/knowledge-items/${row.id}`)">
            {{ row.title }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="索引状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="noteStatusType(row.indexStatus)">{{
            row.indexStatus
          }}</el-tag>
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
      :title="editingId === null ? '新建知识库' : '编辑知识库'"
      width="480px"
    >
      <el-form label-width="72px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="200" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="2000"
            placeholder="知识库用途说明（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false"> 取消 </el-button>
        <el-button type="primary" :disabled="!form.name?.trim()" @click="submit"> 保存 </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="noteDialogVisible" title="新建笔记" width="640px">
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
  max-width: 960px;
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
.desc {
  color: #666;
}
.notes-heading {
  margin: 24px 0 12px;
  font-size: 1.1rem;
}
</style>
