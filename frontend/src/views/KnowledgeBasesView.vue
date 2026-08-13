<script setup lang="ts">
// 知识库列表页：库 CRUD + 库内文档数；库名链接进库详情（P2 拆分的库列表层）。
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
import { errorText } from '../utils/errorText'
import KfEmptyState from '../components/KfEmptyState.vue'

const router = useRouter()

const loading = ref(false)
const items = ref<KnowledgeBaseResponse[]>([])

const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const form = reactive<{ name: string; description: string }>({
  name: '',
  description: '',
})

const emptyState = ref(true)
/** 行级操作守卫：避免切换状态/删除重复提交。 */
const busyRowId = ref<string | null>(null)

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

onMounted(load)
</script>

<template>
  <div class="knowledge-bases-page kf-list-page">
    <div class="page-header kf-list-page-header">
      <h2>知识库</h2>
      <div class="kf-list-page-actions">
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
      <el-table-column prop="name" label="名称" min-width="180">
        <template #default="{ row }">
          <el-link type="primary" @click="router.push(`/knowledge-bases/${row.id}`)">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="240">
        <template #default="{ row }">
          <span class="desc">{{ row.description || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="documentCount" label="文档数" width="110">
        <template #default="{ row }">{{ row.documentCount ?? 0 }}</template>
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
