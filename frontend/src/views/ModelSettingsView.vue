<script setup lang="ts">
// 模型设置页：模型配置 CRUD、连接/Utility 能力测试、默认 Chat/Utility 角色设置。
// API Key 始终只显示掩码；编辑时未显式输入新 Key 则保持原值不变。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createModelConfig,
  deleteModelConfig,
  disableModelConfig,
  enableModelConfig,
  getOwnerAiSettings,
  listModelConfigs,
  testConnection,
  testUtilityCapability,
  updateModelConfig,
  updateOwnerAiSettings,
} from '../api/model-configs'
import type {
  CreateModelConfigRequest,
  ModelConfigResponse,
  OwnerAiSettingsResponse,
  UpdateModelConfigRequest,
} from '../api/types/model-config'
import { errorText } from '../utils/errorText'
import KfEmptyState from '../components/KfEmptyState.vue'

const loading = ref(false)
const items = ref<ModelConfigResponse[]>([])
const settings = ref<OwnerAiSettingsResponse | null>(null)

const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({
  displayName: '',
  providerName: '',
  baseUrl: '',
  modelName: '',
  temperature: 0.7,
  maxOutputTokens: 2048,
  apiKey: '',
})

const testing = ref<Record<string, string>>({}) // configId -> 'connection' | 'utility'
/** 行级操作守卫：避免切换状态/删除/设置角色重复提交。 */
const busyRowId = ref<string | null>(null)

async function load() {
  loading.value = true
  try {
    items.value = await listModelConfigs()
    settings.value = await getOwnerAiSettings()
  } catch (e) {
    ElMessage.error(errorText(e, '加载模型配置失败'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.displayName = ''
  form.providerName = ''
  form.baseUrl = ''
  form.modelName = ''
  form.temperature = 0.7
  form.maxOutputTokens = 2048
  form.apiKey = ''
  dialogVisible.value = true
}

function goCreate() {
  openCreate()
}

function openEdit(row: ModelConfigResponse) {
  editingId.value = row.id
  const rev = row.currentRevision
  form.displayName = row.displayName
  form.providerName = row.providerName
  form.baseUrl = rev?.baseUrl ?? ''
  form.modelName = rev?.modelName ?? ''
  form.temperature = rev?.temperature ?? 0.7
  form.maxOutputTokens = rev?.maxOutputTokens ?? 2048
  // 掩码不可编辑；如需更换 Key，用户显式输入新 Key
  form.apiKey = ''
  dialogVisible.value = true
}

async function submit() {
  try {
    if (editingId.value === null) {
      const payload: CreateModelConfigRequest = {
        displayName: form.displayName.trim(),
        providerName: form.providerName.trim(),
        baseUrl: form.baseUrl.trim(),
        modelName: form.modelName.trim(),
        temperature: form.temperature,
        maxOutputTokens: form.maxOutputTokens,
        apiKey: form.apiKey.trim(),
      }
      if (!payload.apiKey) {
        ElMessage.warning('新建配置必须填写 API Key')
        return
      }
      await createModelConfig(payload)
      ElMessage.success('模型配置已创建')
    } else {
      const payload: UpdateModelConfigRequest = {}
      if (form.displayName !== undefined) payload.displayName = form.displayName.trim()
      if (form.providerName !== undefined) payload.providerName = form.providerName.trim()
      if (form.baseUrl !== undefined) payload.baseUrl = form.baseUrl.trim()
      if (form.modelName !== undefined) payload.modelName = form.modelName.trim()
      if (form.temperature !== undefined) payload.temperature = form.temperature
      if (form.maxOutputTokens !== undefined) payload.maxOutputTokens = form.maxOutputTokens
      // 只有用户显式输入了新的 API Key 才传递，避免把掩码当 Key 保存
      if (form.apiKey.trim()) payload.apiKey = form.apiKey.trim()
      await updateModelConfig(editingId.value, payload)
      ElMessage.success('模型配置已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '保存失败'))
  } finally {
    form.apiKey = ''
  }
}

async function toggleEnabled(row: ModelConfigResponse) {
  if (busyRowId.value) return
  busyRowId.value = row.id
  try {
    if (row.enabled) {
      await disableModelConfig(row.id)
      ElMessage.info(`已禁用「${row.displayName}」`)
    } else {
      await enableModelConfig(row.id)
      ElMessage.success(`已启用「${row.displayName}」`)
    }
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '切换状态失败'))
  } finally {
    busyRowId.value = null
  }
}

async function confirmDelete(row: ModelConfigResponse) {
  if (busyRowId.value) return
  busyRowId.value = row.id
  try {
    await ElMessageBox.confirm(
      `确定删除模型配置「${row.displayName}」吗？历史 Revision 将保留但不再可用。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await deleteModelConfig(row.id)
    ElMessage.success('模型配置已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(errorText(e, '删除失败'))
    }
  } finally {
    busyRowId.value = null
  }
}

async function runTest(row: ModelConfigResponse, kind: 'connection' | 'utility') {
  testing.value[row.id] = kind
  try {
    if (kind === 'connection') {
      const result = await testConnection(row.id)
      if (result.success) {
        ElMessage.success(`连接测试通过：${result.message}`)
      } else {
        ElMessage.error(`连接测试失败：${result.message}`)
      }
    } else {
      const result = await testUtilityCapability(row.id)
      if (result.success) {
        ElMessage.success(
          `Utility 能力测试通过（Router: ${result.routerSchemaValid}, Candidate: ${result.candidateSchemaValid}）`,
        )
        await load()
      } else {
        ElMessage.error(`Utility 能力测试失败：${result.message}`)
      }
    }
  } catch (e) {
    ElMessage.error(errorText(e, '测试失败'))
  } finally {
    testing.value[row.id] = ''
  }
}

/** 把该配置设为默认 Chat Model。Utility 为空时回退为同一配置，保证后端必填约束满足。 */
async function setDefaultChat(row: ModelConfigResponse) {
  if (!settings.value || busyRowId.value) return
  busyRowId.value = row.id
  try {
    await updateOwnerAiSettings({
      defaultChatModelConfigId: row.id,
      utilityModelConfigId: settings.value.utilityModelConfigId ?? row.id,
    })
    ElMessage.success(`已设为默认 Chat Model：${row.displayName}`)
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '设置默认 Chat Model 失败'))
  } finally {
    busyRowId.value = null
  }
}

/** 把该配置设为 Utility Model。保留现有默认 Chat，避免覆盖用户选择。 */
async function setUtility(row: ModelConfigResponse) {
  if (!settings.value || busyRowId.value) return
  busyRowId.value = row.id
  try {
    await updateOwnerAiSettings({
      defaultChatModelConfigId: settings.value.defaultChatModelConfigId ?? undefined,
      utilityModelConfigId: row.id,
    })
    ElMessage.success(`已设为 Utility Model：${row.displayName}`)
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '设置 Utility Model 失败'))
  } finally {
    busyRowId.value = null
  }
}

function isDefaultChat(id: string): boolean {
  return settings.value?.defaultChatModelConfigId === id
}

function isUtility(id: string): boolean {
  return settings.value?.utilityModelConfigId === id
}

onMounted(load)
</script>

<template>
  <div class="model-settings-page">
    <div class="page-header">
      <h2>模型设置</h2>
      <el-button type="primary" @click="openCreate"> 新建模型配置 </el-button>
    </div>

    <KfEmptyState
      v-if="!loading && items.length === 0"
      title="先接入一个可以对话的模型"
      description="模型配置完成并通过连接测试后，知流才能陪你对话、提炼并再次调用知识。"
      action-label="新建模型配置"
      @action="goCreate"
    />

    <section v-else v-loading="loading" class="config-list" aria-label="模型配置列表">
      <article v-for="row in items" :key="row.id" class="config-card">
        <header class="config-card-head">
          <div class="config-identity">
            <span class="config-kicker">{{ row.providerName }}</span>
            <h3>{{ row.displayName }}</h3>
          </div>
          <div class="config-badges">
            <el-tag :type="row.enabled ? 'success' : 'info'" round>
              {{ row.enabled ? '已启用' : '已禁用' }}
            </el-tag>
            <el-tag v-if="isDefaultChat(row.id)" type="success" effect="dark" round>
              默认 Chat
            </el-tag>
            <el-tag v-if="isUtility(row.id)" type="warning" effect="dark" round> Utility </el-tag>
          </div>
        </header>

        <dl class="config-details">
          <div>
            <dt>Model</dt>
            <dd>{{ row.currentRevision?.modelName || '—' }}</dd>
          </div>
          <div class="config-url">
            <dt>Base URL</dt>
            <dd :title="row.currentRevision?.baseUrl">
              {{ row.currentRevision?.baseUrl || '—' }}
            </dd>
          </div>
          <div>
            <dt>API Key</dt>
            <dd class="masked">{{ row.currentRevision?.apiKeyMasked || '—' }}</dd>
          </div>
          <div>
            <dt>参数</dt>
            <dd>
              T {{ row.currentRevision?.temperature ?? '—' }} · Max
              {{ row.currentRevision?.maxOutputTokens ?? '—' }}
            </dd>
          </div>
        </dl>

        <footer class="config-actions">
          <div class="config-actions-main">
            <el-button
              :loading="testing[row.id] === 'connection'"
              @click="runTest(row, 'connection')"
            >
              测试连接
            </el-button>
            <el-button :loading="testing[row.id] === 'utility'" @click="runTest(row, 'utility')">
              测试 Utility
            </el-button>
            <el-button @click="openEdit(row)">编辑</el-button>
            <el-button
              :type="row.enabled ? 'warning' : 'success'"
              :loading="busyRowId === row.id"
              :disabled="busyRowId !== null && busyRowId !== row.id"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button
              class="config-delete"
              text
              type="danger"
              :loading="busyRowId === row.id"
              :disabled="busyRowId !== null && busyRowId !== row.id"
              @click="confirmDelete(row)"
            >
              删除配置
            </el-button>
          </div>
          <div class="config-role-actions" aria-label="模型角色设置">
            <el-button
              :disabled="!row.enabled || isDefaultChat(row.id)"
              :loading="busyRowId === row.id"
              @click="setDefaultChat(row)"
            >
              {{ isDefaultChat(row.id) ? '已是默认' : '设为默认' }}
            </el-button>
            <el-button
              type="primary"
              :disabled="!row.enabled || isUtility(row.id)"
              :loading="busyRowId === row.id"
              @click="setUtility(row)"
            >
              {{ isUtility(row.id) ? '已是 Utility' : '设为 Utility' }}
            </el-button>
          </div>
        </footer>
      </article>
    </section>

    <el-dialog
      v-model="dialogVisible"
      width="min(520px, 94vw)"
      align-center
      :show-close="false"
      class="kf-dialog"
    >
      <div class="kf-dialog-inner">
        <div class="kf-dialog-head">
          <div class="kf-dialog-tag">{{ editingId === null ? '新建' : '编辑' }}</div>
          <h3 class="kf-dialog-title">
            {{ editingId === null ? '新建模型配置' : '编辑模型配置' }}
          </h3>
          <p class="kf-dialog-sub">告诉知流用哪个模型来思考，以及怎么调它的参数。</p>
          <button class="kf-dialog-close" aria-label="关闭" @click="dialogVisible = false">
            ✕
          </button>
        </div>
        <el-form class="kf-dialog-form" label-position="top">
          <div class="kf-form-grid">
            <el-form-item label="显示名称" required>
              <el-input
                v-model="form.displayName"
                maxlength="200"
                placeholder="例如 DeepSeek Chat"
              />
            </el-form-item>
            <el-form-item label="Provider" required>
              <el-input v-model="form.providerName" maxlength="100" placeholder="例如 DeepSeek" />
            </el-form-item>
          </div>
          <el-form-item label="Base URL" required>
            <el-input
              v-model="form.baseUrl"
              maxlength="500"
              placeholder="https://api.deepseek.com"
            />
            <div class="hint">仅允许安全 HTTPS 云端地址（阻止 localhost/私网/内嵌凭据）</div>
          </el-form-item>
          <el-form-item label="Model Name" required>
            <el-input v-model="form.modelName" maxlength="200" placeholder="deepseek-chat" />
          </el-form-item>
          <div class="kf-form-grid">
            <el-form-item label="Temperature">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
            </el-form-item>
            <el-form-item label="Max Tokens">
              <el-input-number v-model="form.maxOutputTokens" :min="1" :max="1000000" :step="128" />
            </el-form-item>
          </div>
          <el-form-item :label="editingId === null ? 'API Key' : '新 API Key'">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              :placeholder="editingId === null ? 'sk-...' : '留空保持不变（掩码不回显为 Key）'"
            />
          </el-form-item>
        </el-form>
        <div class="kf-dialog-actions">
          <button class="kf-btn kf-btn-ghost" @click="dialogVisible = false">取消</button>
          <button class="kf-btn kf-btn-ink" :disabled="!form.displayName?.trim()" @click="submit">
            保存
          </button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.model-settings-page {
  max-width: 1120px;
  margin: 0 auto;
  padding: 30px clamp(18px, 4vw, 48px) 72px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 900;
  letter-spacing: -0.04em;
}
.config-list {
  display: grid;
  gap: 18px;
  margin-top: 24px;
}
.config-card {
  border: 1px solid var(--kf-ink);
  border-radius: 20px;
  background: var(--kf-white);
  box-shadow: 5px 5px 0 rgba(25, 24, 21, 0.12);
  overflow: hidden;
}
.config-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--kf-line);
  background: var(--kf-paper-2);
}
.config-kicker {
  color: var(--kf-hot);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.config-identity h3 {
  margin: 5px 0 0;
  font-size: 21px;
  font-weight: 900;
  letter-spacing: -0.04em;
}
.config-badges {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
.config-details {
  display: grid;
  grid-template-columns: minmax(130px, 0.8fr) minmax(230px, 2fr) minmax(140px, 1fr) minmax(
      160px,
      1fr
    );
  margin: 0;
  padding: 22px 24px;
}
.config-details > div {
  min-width: 0;
  padding: 0 18px;
  border-left: 1px dashed var(--kf-line-dashed);
}
.config-details > div:first-child {
  padding-left: 0;
  border-left: 0;
}
.config-details dt {
  margin-bottom: 7px;
  color: var(--kf-muted);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 0.08em;
}
.config-details dd {
  margin: 0;
  min-width: 0;
  overflow: hidden;
  color: var(--kf-ink);
  font-size: 13px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.masked {
  font-family: var(--kf-font-mono);
  color: var(--kf-muted) !important;
}
.config-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 24px 20px;
  border-top: 1px dashed var(--kf-line-dashed);
}
.config-actions-main,
.config-role-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.config-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.config-role-actions {
  justify-content: flex-end;
  padding-left: 18px;
  border-left: 1px solid var(--kf-line);
}
.config-delete {
  padding-inline: 10px;
}
.hint {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
@media (max-width: 920px) {
  .config-details {
    grid-template-columns: 1fr 1fr;
    row-gap: 20px;
  }
  .config-details > div:nth-child(3) {
    padding-left: 0;
    border-left: 0;
  }
  .config-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .config-role-actions {
    justify-content: flex-start;
    padding: 14px 0 0;
    border-top: 1px solid var(--kf-line);
    border-left: 0;
  }
}
@media (max-width: 560px) {
  .model-settings-page {
    padding-inline: 14px;
  }
  .config-card-head {
    flex-direction: column;
  }
  .config-badges {
    justify-content: flex-start;
  }
  .config-details {
    grid-template-columns: 1fr;
  }
  .config-details > div {
    padding: 14px 0 0;
    border-top: 1px dashed var(--kf-line-dashed);
    border-left: 0;
  }
  .config-details > div:first-child {
    padding-top: 0;
    border-top: 0;
  }
  .kf-form-grid {
    grid-template-columns: 1fr;
  }
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
.kf-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
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
