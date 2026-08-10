<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
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

const loading = ref(false)
const items = ref<KnowledgeBaseResponse[]>([])

const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const form = reactive<{ name: string; description: string }>({
  name: '',
  description: '',
})

const emptyState = ref(true)

async function load() {
  loading.value = true
  try {
    items.value = await listKnowledgeBases()
    emptyState.value = items.value.length === 0
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
    await ElMessageBox.confirm(`确定删除知识库「${row.name}」吗？此操作不可恢复。`, '删除确认', {
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

function errorText(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    return e.message || e.errorCode || fallback
  }
  return e instanceof Error ? e.message : fallback
}

onMounted(load)
</script>

<template>
  <div class="knowledge-bases-page">
    <div class="page-header">
      <h2>知识库</h2>
      <el-button type="primary" @click="openCreate"> 新建知识库 </el-button>
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
</style>
