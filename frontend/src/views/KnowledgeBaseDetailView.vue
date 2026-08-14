<script setup lang="ts">
// 知识库详情页：该库文档列表 + 过滤（来源/标签/索引状态）+ 分页；新建笔记/上传入口（归属当前库）。
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getKnowledgeBase } from '../api/knowledge-bases'
import type { KnowledgeBaseResponse } from '../api/types/knowledge-base'
import { createDocument, listDocuments } from '../api/documents'
import type { KnowledgeDocumentSummaryResponse } from '../api/types/document'
import { listTags } from '../api/tags'
import type { TagResponse } from '../api/types/tag'
import { uploadFile } from '../api/files'
import { errorText } from '../utils/errorText'
import { SOURCE_TYPE_OPTIONS, sourceTypeLabel } from '../utils/sourceType'
import KfEmptyState from '../components/KfEmptyState.vue'
import DocumentStatusCell from '../components/DocumentStatusCell.vue'

const route = useRoute()
const router = useRouter()

const kbId = String(route.params.id)
const loading = ref(true)
const kb = ref<KnowledgeBaseResponse | null>(null)
const tags = ref<TagResponse[]>([])
const documents = ref<KnowledgeDocumentSummaryResponse[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const sourceType = ref('')
const tag = ref('')
const indexStatus = ref('')

const noteDialogVisible = ref(false)
const noteForm = reactive<{ title: string; summary: string; content: string; tags: string }>({
  title: '',
  summary: '',
  content: '',
  tags: '',
})

const uploadDialogVisible = ref(false)
const uploadFileRaw = ref<File | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)

const MAX_FILE_BYTES = 5 * 1024 * 1024

const SOURCE_OPTIONS = [{ value: '', label: '全部来源' }, ...SOURCE_TYPE_OPTIONS]

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待索引' },
  { value: 'PROCESSING', label: '索引中' },
  { value: 'INDEXED', label: '已索引' },
  { value: 'FAILED', label: '索引失败' },
]

async function load() {
  loading.value = true
  try {
    const [kbResult, tagResult, pageResult] = await Promise.all([
      getKnowledgeBase(kbId),
      listTags(),
      listDocuments({
        knowledgeBaseId: kbId,
        sourceType: sourceType.value || undefined,
        tag: tag.value || undefined,
        indexStatus: indexStatus.value || undefined,
        page: page.value,
        size: pageSize.value,
      }),
    ])
    kb.value = kbResult
    tags.value = tagResult
    documents.value = pageResult.items ?? []
    total.value = pageResult.total ?? 0
  } catch (e) {
    ElMessage.error(errorText(e, '加载文档列表失败'))
  } finally {
    loading.value = false
  }
}

/** 过滤条件变化回到第一页再加载。 */
watch([sourceType, tag, indexStatus], () => {
  page.value = 1
  void load()
})

function handlePageChange(next: number) {
  page.value = next
  void load()
}

function openDocument(row: KnowledgeDocumentSummaryResponse) {
  void router.push(`/documents/${row.id}`)
}

function openCreateNote() {
  noteForm.title = ''
  noteForm.summary = ''
  noteForm.content = ''
  noteForm.tags = ''
  noteDialogVisible.value = true
}

function openUpload() {
  uploadFileRaw.value = null
  uploadProgress.value = 0
  uploadDialogVisible.value = true
}

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
  uploading.value = true
  uploadProgress.value = 10
  try {
    const result = await uploadFile(uploadFileRaw.value, kbId)
    uploadProgress.value = 100
    uploadDialogVisible.value = false
    await load()
    if (result.item?.id) {
      if (result.duplicate) {
        ElMessage.info('已存在相同内容的文件，跳转到已有条目')
      } else {
        ElMessage.success('文件已上传，正在解析与索引')
      }
      router.push(`/documents/${result.item.id}`)
    }
  } catch (e) {
    ElMessage.error(errorText(e, '上传失败'))
  } finally {
    uploading.value = false
  }
}

async function submitNote() {
  try {
    const created = await createDocument({
      title: noteForm.title.trim() || undefined,
      summary: noteForm.summary,
      content: noteForm.content,
      knowledgeBaseId: kbId,
      tags: noteForm.tags
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean),
    })
    ElMessage.success('笔记已创建，索引处理中')
    noteDialogVisible.value = false
    await load()
    router.push(`/documents/${created.id}`)
  } catch (e) {
    ElMessage.error(errorText(e, '创建笔记失败'))
  }
}

onMounted(load)
</script>

<template>
  <div class="kb-detail-page kf-list-page">
    <div class="kb-detail-header">
      <div class="kb-detail-title-row">
        <el-button link @click="router.push('/knowledge-bases')">← 返回知识库</el-button>
        <div class="kb-title-wrap">
          <h2>{{ kb?.name ?? '…' }}</h2>
          <el-tag v-if="kb" size="small" :type="kb.enabled ? 'success' : 'info'">
            {{ kb.enabled ? '已启用' : '已禁用' }}
          </el-tag>
        </div>
      </div>
      <p v-if="kb?.description" class="kb-desc">{{ kb.description }}</p>
      <div class="kf-list-page-actions kb-actions">
        <el-button type="primary" plain @click="openCreateNote"> 新建笔记 </el-button>
        <el-button type="primary" plain @click="openUpload"> 上传文件 </el-button>
      </div>
    </div>

    <div class="filters-bar">
      <el-select v-model="sourceType" class="filter-select" aria-label="来源过滤">
        <el-option v-for="o in SOURCE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select
        v-model="tag"
        class="filter-select"
        clearable
        placeholder="标签过滤"
        aria-label="标签过滤"
      >
        <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.name" />
      </el-select>
      <el-select v-model="indexStatus" class="filter-select" aria-label="索引状态过滤">
        <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
    </div>

    <KfEmptyState
      v-if="!loading && documents.length === 0"
      title="这个知识库还没有文档"
      description="新建一条笔记，或上传 Markdown / TXT 文件，知识就会在这里生长。"
      action-label="新建笔记"
      wide
      @action="openCreateNote"
    />

    <el-table
      v-else
      v-loading="loading"
      :data="documents"
      style="width: 100%"
      @row-click="openDocument"
    >
      <el-table-column prop="title" label="标题" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="openDocument(row)">{{ row.title }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="110">
        <template #default="{ row }">{{ sourceTypeLabel(row.sourceType) }}</template>
      </el-table-column>
      <el-table-column prop="indexStatus" label="索引状态" width="130">
        <template #default="{ row }">
          <DocumentStatusCell :row="row" />
        </template>
      </el-table-column>
      <el-table-column prop="contentVersion" label="版本" width="80">
        <template #default="{ row }">v{{ row.contentVersion }}</template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString() }}</template>
      </el-table-column>
    </el-table>

    <div v-if="total > pageSize" class="pagination-wrap">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

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
        <el-form-item v-if="uploading" label="进度">
          <el-progress :percentage="uploadProgress" :stroke-width="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false"> 取消 </el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!uploadFileRaw"
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
        <el-form-item label="知识库">
          <el-input :model-value="kb?.name ?? ''" disabled />
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
.kb-detail-page {
  width: 100%;
}
.kb-detail-header {
  margin-bottom: 14px;
}
.kb-detail-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.kb-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kb-title-wrap h2 {
  margin: 0;
  font-size: 1.25rem;
}
.kb-desc {
  margin: 8px 0 0 12px;
  color: #666;
  font-size: 0.9rem;
}
.kb-actions {
  margin-top: 12px;
}
.filters-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.filter-select {
  width: 150px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.upload-tip {
  color: #999;
  font-size: 0.8rem;
  margin-top: 4px;
}
</style>
