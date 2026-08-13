<script setup lang="ts">
// 候选审核页面：AI 原值/草稿并列展示；编辑、拒绝/恢复、幂等确认；确认后跳转 Item 详情。
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmCandidate,
  listCandidates,
  rejectCandidate,
  restoreCandidate,
  updateCandidateDraft,
} from '../api/extraction'
import type { CandidateResponse } from '../api/extraction'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'
import { listKnowledgeBases } from '../api/knowledge-bases'
import { errorText } from '../utils/errorText'
import KfEmptyState from '../components/KfEmptyState.vue'

const router = useRouter()
const loading = ref(false)
const items = ref<CandidateResponse[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const filter = ref<'PENDING' | 'CONFIRMED' | 'REJECTED' | 'ALL'>('PENDING')
const knowledgeBases = ref<KnowledgeBaseResponse[]>([])

// 详情抽屉
const drawerOpen = ref(false)
const editing = ref(false)
const current = ref<CandidateResponse | null>(null)
const draftTitle = ref('')
const draftSummary = ref('')
const draftContent = ref('')
const draftKbId = ref('')
const draftTags = ref<string[]>([])
const tagsInput = ref('')
const savingDraft = ref(false)
const confirmingId = ref<string | null>(null)
/** 行级操作守卫：避免拒绝/恢复重复提交。 */
const busyActionId = ref<string | null>(null)

async function load() {
  loading.value = true
  try {
    const status = filter.value === 'ALL' ? undefined : filter.value
    const res = await listCandidates({ status, page: page.value, size: pageSize })
    items.value = res.items ?? []
    total.value = res.total ?? 0
  } catch (e) {
    ElMessage.error(errorText(e, '加载候选失败'))
  } finally {
    loading.value = false
  }
}

async function onFilterChange() {
  page.value = 1
  await load()
}

function openDrawer(candidate: CandidateResponse) {
  current.value = candidate
  editing.value = false
  draftTitle.value = candidate.draftTitle ?? ''
  draftSummary.value = candidate.draftSummary ?? ''
  draftContent.value = candidate.draftContent ?? ''
  draftKbId.value = candidate.draftKnowledgeBaseId ?? ''
  draftTags.value = [...(candidate.draftTags ?? [])]
  tagsInput.value = draftTags.value.join(', ')
  drawerOpen.value = true
}

function startEdit() {
  editing.value = true
  tagsInput.value = draftTags.value.join(', ')
}

async function saveDraft() {
  if (!current.value || !current.value.id) return
  savingDraft.value = true
  try {
    const tags = tagsInput.value
      .split(/[,，]/)
      .map((t) => t.trim())
      .filter(Boolean)
    const updated = await updateCandidateDraft(
      current.value.id,
      {
        title: draftTitle.value,
        summary: draftSummary.value,
        content: draftContent.value,
        knowledgeBaseId: draftKbId.value,
        tags,
      },
      `"${current.value.rowVersion ?? 0}"`,
    )
    ElMessage.success('草稿已保存')
    editing.value = false
    current.value = updated
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '保存草稿失败'))
  } finally {
    savingDraft.value = false
  }
}

async function confirmCandidateRow(candidate: CandidateResponse) {
  if (!candidate.id || confirmingId.value) return
  confirmingId.value = candidate.id
  try {
    const updated = await confirmCandidate(candidate.id)
    ElMessage.success('已确认并创建知识条目')
    if (updated.confirmedItemId) {
      await router.push(`/documents/${updated.confirmedItemId}`)
      return
    }
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '确认失败'))
  } finally {
    confirmingId.value = null
  }
}

async function rejectRow(candidate: CandidateResponse) {
  if (!candidate.id || busyActionId.value) return
  busyActionId.value = candidate.id
  try {
    await ElMessageBox.confirm('拒绝该候选？将不创建知识条目。', '拒绝候选', { type: 'warning' })
    const updated = await rejectCandidate(candidate.id, `"${candidate.rowVersion ?? 0}"`)
    ElMessage.success('已拒绝')
    if (current.value?.id === candidate.id) {
      current.value = updated
    }
    await load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(errorText(e, '拒绝失败'))
  } finally {
    busyActionId.value = null
  }
}

async function restoreRow(candidate: CandidateResponse) {
  if (!candidate.id || busyActionId.value) return
  busyActionId.value = candidate.id
  try {
    await restoreCandidate(candidate.id, `"${candidate.rowVersion ?? 0}"`)
    ElMessage.success('已恢复为待审核')
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '恢复失败'))
  } finally {
    busyActionId.value = null
  }
}

function goItem(id?: string) {
  if (!id) return
  void router.push(`/documents/${id}`)
}

function goChat() {
  void router.push('/chat')
}

function statusType(status?: string): 'warning' | 'success' | 'danger' {
  if (status === 'CONFIRMED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function statusText(status?: string): string {
  if (status === 'CONFIRMED') return '已确认'
  if (status === 'REJECTED') return '已拒绝'
  return '待审核'
}

onMounted(async () => {
  try {
    knowledgeBases.value = await listKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  }
  await load()
})
</script>

<template>
  <div class="candidates-page kf-list-page">
    <div class="page-header kf-list-page-header">
      <h2>待审核候选</h2>
      <div class="header-controls kf-list-page-actions">
        <el-radio-group v-model="filter" @change="onFilterChange">
          <el-radio-button value="PENDING">待审核</el-radio-button>
          <el-radio-button value="CONFIRMED">已确认</el-radio-button>
          <el-radio-button value="REJECTED">已拒绝</el-radio-button>
          <el-radio-button value="ALL">全部</el-radio-button>
        </el-radio-group>
        <el-button @click="load"> 刷新 </el-button>
      </div>
    </div>

    <KfEmptyState
      v-if="!loading && items.length === 0"
      title="还没有等待沉淀的知识"
      description="在 AI 对话中把问题聊透，再点击「提取知识」，值得保留的理解会来到这里。"
      action-label="去 AI 对话"
      wide
      @action="goChat"
    />

    <div v-if="loading || items.length > 0" v-loading="loading" class="candidate-list">
      <div v-for="c in items" :key="c.id" class="candidate-card">
        <div class="candidate-head">
          <span class="candidate-title">{{ c.draftTitle || c.aiTitle || '(无标题)' }}</span>
          <el-tag :type="statusType(c.status)" size="small">{{ statusText(c.status) }}</el-tag>
        </div>
        <div class="candidate-meta">
          任务 #{{ c.extractionTaskId }} · 候选 #{{ c.id }}
          <button
            v-if="c.status === 'CONFIRMED' && c.confirmedItemId"
            type="button"
            class="item-link"
            @click="goItem(c.confirmedItemId)"
          >
            → 知识条目 #{{ c.confirmedItemId }}
          </button>
        </div>
        <div class="candidate-actions">
          <el-button size="small" @click="openDrawer(c)"> 查看/编辑 </el-button>
          <el-button
            v-if="c.status === 'PENDING'"
            size="small"
            type="primary"
            :loading="confirmingId === c.id"
            @click="confirmCandidateRow(c)"
          >
            确认
          </el-button>
          <el-button
            v-if="c.status === 'PENDING'"
            size="small"
            type="danger"
            plain
            :loading="busyActionId === c.id"
            :disabled="busyActionId !== null && busyActionId !== c.id"
            @click="rejectRow(c)"
          >
            拒绝
          </el-button>
          <el-button
            v-if="c.status === 'REJECTED'"
            size="small"
            :loading="busyActionId === c.id"
            :disabled="busyActionId !== null && busyActionId !== c.id"
            @click="restoreRow(c)"
          >
            恢复
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="items.length > 0" class="pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="load"
      />
    </div>

    <el-drawer v-model="drawerOpen" size="min(60%, 720px)" :title="`候选 #${current?.id}`">
      <template v-if="current">
        <el-divider content-position="left">AI 原始结果（不可修改）</el-divider>
        <div class="ai-panel">
          <div class="ai-row"><b>标题：</b>{{ current.aiTitle }}</div>
          <div v-if="current.aiSummary" class="ai-row"><b>摘要：</b>{{ current.aiSummary }}</div>
          <div class="ai-row ai-content">{{ current.aiContent }}</div>
          <div class="ai-row">
            <b>知识库：</b>
            <el-tag v-if="current.aiKnowledgeBaseId" size="small">
              {{ current.aiKnowledgeBaseId }}
            </el-tag>
            <el-tag v-for="t in current.aiTags" :key="t" size="small" type="info">{{ t }}</el-tag>
          </div>
          <div v-if="current.aiReason" class="ai-row"><b>理由：</b>{{ current.aiReason }}</div>
        </div>

        <el-divider content-position="left">编辑草稿</el-divider>
        <el-form label-width="72px" :disabled="!editing">
          <el-form-item label="标题">
            <el-input v-model="draftTitle" data-testid="draft-title" />
          </el-form-item>
          <el-form-item label="摘要">
            <el-input v-model="draftSummary" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="正文">
            <el-input
              v-model="draftContent"
              type="textarea"
              :rows="8"
              data-testid="draft-content"
            />
          </el-form-item>
          <el-form-item label="知识库">
            <el-select v-model="draftKbId" placeholder="选择知识库" style="width: 100%">
              <el-option
                v-for="kb in knowledgeBases"
                :key="kb.id"
                :label="kb.name"
                :value="kb.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="tagsInput" placeholder="逗号分隔" />
          </el-form-item>
        </el-form>
        <div class="drawer-actions">
          <template v-if="!editing">
            <el-button v-if="current.status === 'PENDING'" type="primary" @click="startEdit">
              编辑草稿
            </el-button>
            <el-button
              v-if="current.status === 'PENDING'"
              type="primary"
              :loading="confirmingId === current.id"
              @click="confirmCandidateRow(current)"
            >
              确认
            </el-button>
            <el-button
              v-if="current.status === 'PENDING'"
              type="danger"
              plain
              @click="rejectRow(current)"
            >
              拒绝
            </el-button>
          </template>
          <template v-else>
            <el-button type="primary" :loading="savingDraft" @click="saveDraft">
              保存草稿
            </el-button>
            <el-button @click="editing = false"> 取消 </el-button>
          </template>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.candidates-page {
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
.candidate-card {
  border: 1px solid var(--kf-ink);
  border-radius: var(--kf-radius-lg);
  padding: 16px 18px;
  margin-bottom: 14px;
  background: var(--kf-white);
}
.candidate-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.candidate-title {
  font-weight: 600;
  font-size: 1rem;
}
.candidate-meta {
  color: #909399;
  font-size: 0.85rem;
  margin: 4px 0 8px;
}
.candidate-meta a {
  color: #409eff;
  cursor: pointer;
  margin-left: 8px;
}
.item-link {
  appearance: none;
  background: none;
  border: 0;
  padding: 4px 0;
  margin-left: 8px;
  color: #409eff;
  cursor: pointer;
  font: inherit;
  font-size: inherit;
}
.item-link:focus-visible {
  outline: var(--kf-focus-ring);
  outline-offset: 2px;
}
.candidate-actions {
  display: flex;
  gap: 8px;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.ai-panel {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
}
.ai-row {
  margin-bottom: 6px;
  font-size: 0.9rem;
}
.ai-content {
  white-space: pre-wrap;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 8px;
  max-height: 240px;
  overflow: auto;
}
.drawer-actions {
  margin-top: 16px;
}
</style>
