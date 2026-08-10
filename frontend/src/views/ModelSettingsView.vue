<script setup lang="ts">
// 模型设置页：模型配置 CRUD、连接/Utility 能力测试、默认 Chat/Utility 角色设置。
// API Key 始终只显示掩码；编辑时未显式输入新 Key 则保持原值不变。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ApiError,
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
  }
}

async function confirmDelete(row: ModelConfigResponse) {
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
  if (!settings.value) return
  try {
    await updateOwnerAiSettings({
      defaultChatModelConfigId: row.id,
      utilityModelConfigId: settings.value.utilityModelConfigId ?? row.id,
    })
    ElMessage.success(`已设为默认 Chat Model：${row.displayName}`)
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '设置默认 Chat Model 失败'))
  }
}

/** 把该配置设为 Utility Model。保留现有默认 Chat，避免覆盖用户选择。 */
async function setUtility(row: ModelConfigResponse) {
  if (!settings.value) return
  try {
    await updateOwnerAiSettings({
      defaultChatModelConfigId: settings.value.defaultChatModelConfigId ?? undefined,
      utilityModelConfigId: row.id,
    })
    ElMessage.success(`已设为 Utility Model：${row.displayName}`)
    await load()
  } catch (e) {
    ElMessage.error(errorText(e, '设置 Utility Model 失败'))
  }
}

function errorText(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    return e.message || e.errorCode || fallback
  }
  return e instanceof Error ? e.message : fallback
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

    <el-empty
      v-if="!loading && items.length === 0"
      description="还没有模型配置，点击右上角创建第一个"
    />

    <el-table v-else v-loading="loading" :data="items" style="width: 100%">
      <el-table-column prop="displayName" label="名称" min-width="140" />
      <el-table-column prop="providerName" label="Provider" width="120" />
      <el-table-column label="Model" min-width="150">
        <template #default="{ row }">
          {{ row.currentRevision?.modelName || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="Base URL" min-width="200">
        <template #default="{ row }">
          <span class="url">{{ row.currentRevision?.baseUrl || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="API Key" width="140">
        <template #default="{ row }">
          <span class="masked">{{ row.currentRevision?.apiKeyMasked || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="140">
        <template #default="{ row }">
          <el-tag v-if="isDefaultChat(row.id)" type="success" size="small">默认 Chat</el-tag>
          <el-tag v-if="isUtility(row.id)" type="warning" size="small">Utility</el-tag>
          <span v-if="!isDefaultChat(row.id) && !isUtility(row.id)">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '已启用' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360">
        <template #default="{ row }">
          <el-button
            size="small"
            :loading="testing[row.id] === 'connection'"
            @click="runTest(row, 'connection')"
          >
            测试连接
          </el-button>
          <el-button
            size="small"
            :loading="testing[row.id] === 'utility'"
            @click="runTest(row, 'utility')"
          >
            Utility 测试
          </el-button>
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
      <el-table-column label="设置" width="200">
        <template #default="{ row }">
          <el-button
            size="small"
            :disabled="!row.enabled || isDefaultChat(row.id)"
            @click="setDefaultChat(row)"
          >
            设为默认
          </el-button>
          <el-button
            size="small"
            :disabled="!row.enabled || isUtility(row.id)"
            @click="setUtility(row)"
          >
            设为 Utility
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建模型配置' : '编辑模型配置'"
      width="520px"
    >
      <el-form label-width="120px">
        <el-form-item label="显示名称" required>
          <el-input v-model="form.displayName" maxlength="200" placeholder="例如 DeepSeek Chat" />
        </el-form-item>
        <el-form-item label="Provider" required>
          <el-input v-model="form.providerName" maxlength="100" placeholder="例如 DeepSeek" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="form.baseUrl" maxlength="500" placeholder="https://api.deepseek.com" />
          <div class="hint">仅允许安全 HTTPS 云端地址（阻止 localhost/私网/内嵌凭据）</div>
        </el-form-item>
        <el-form-item label="Model Name" required>
          <el-input v-model="form.modelName" maxlength="200" placeholder="deepseek-chat" />
        </el-form-item>
        <el-form-item label="Temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="Max Tokens">
          <el-input-number v-model="form.maxOutputTokens" :min="1" :max="1000000" :step="128" />
        </el-form-item>
        <el-form-item :label="editingId === null ? 'API Key' : '新 API Key'">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId === null ? 'sk-...' : '留空保持不变（掩码不回显为 Key）'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false"> 取消 </el-button>
        <el-button type="primary" :disabled="!form.displayName?.trim()" @click="submit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.model-settings-page {
  max-width: 1200px;
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
.url {
  color: #666;
  word-break: break-all;
}
.masked {
  font-family: monospace;
  color: #999;
}
.hint {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
