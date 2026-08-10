<script setup lang="ts">
/* global AbortController, crypto */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createConversation,
  deleteConversation,
  listConversations,
  listMessages,
  streamRegenerate,
  streamSend,
  stopGeneration,
  updateConversation,
} from '../api/conversations'
import { listModelConfigs } from '../api/model-configs'
import type { ConversationResponse, MessageResponse } from '../api/types/conversation'
import type { ModelConfigResponse } from '../api/types/model-config'
import { dispatchSseEvent, useChatStream } from '../composables/useChatStream'
import MessageSourcesPanel from '../components/MessageSourcesPanel.vue'

const conversations = ref<ConversationResponse[]>([])
const activeId = ref<string | null>(null)
const messages = ref<MessageResponse[]>([])
const models = ref<ModelConfigResponse[]>([])
const input = ref('')
const selectedModelId = ref<string | undefined>()
const loadingHistory = ref(false)
const nextBefore = ref<string | null>(null)
const hasMoreHistory = ref(true)
let activeController: AbortController | null = null

const stream = useChatStream({
  getConversation: () => activeConversation.value,
  getMessages: () => messages.value,
  setMessages: (m) => (messages.value = m),
  // 流结束后对账：重新拉取服务器最终状态，确保失败/取消/断连后 UI 与 DB 一致
  reconcile: () => {
    void loadHistory(null)
  },
})

const activeConversation = computed(() => {
  return conversations.value.find((c) => c.id === activeId.value) ?? null
})

const canSend = computed(() => !!activeConversation.value && input.value.trim().length > 0)

onMounted(async () => {
  await Promise.all([loadConversations(), loadModels()])
})

onUnmounted(() => {
  activeController?.abort()
})

async function loadConversations() {
  try {
    conversations.value = await listConversations()
  } catch {
    conversations.value = []
  }
}

async function loadModels() {
  try {
    models.value = (await listModelConfigs()).filter((m) => m.enabled)
  } catch {
    models.value = []
  }
}

async function selectConversation(id: string) {
  activeId.value = id
  const conv = activeConversation.value
  selectedModelId.value = conv?.defaultModelConfigId ?? undefined
  messages.value = []
  nextBefore.value = null
  hasMoreHistory.value = true
  await loadHistory(null)
}

async function loadHistory(before: string | null) {
  if (!activeId.value) return
  loadingHistory.value = true
  try {
    const page = await listMessages(activeId.value, {
      before: before ?? undefined,
      limit: 20,
    })
    const list = page.messages ?? []
    if (before) {
      messages.value = [...list, ...messages.value]
    } else {
      messages.value = list
    }
    nextBefore.value = page.nextBefore ?? null
    hasMoreHistory.value = !!page.nextBefore
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载历史失败')
  } finally {
    loadingHistory.value = false
  }
}

async function handleCreate() {
  try {
    const result = await ElMessageBox.prompt('输入会话标题', '新建会话', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
    })
    const title = result.value.trim()
    if (!title) {
      ElMessage.warning('标题不能为空')
      return
    }
    const created = await createConversation({ title })
    conversations.value.unshift(created)
    await selectConversation(created.id!)
  } catch {
    // 用户取消
  }
}

async function handleRename() {
  if (!activeConversation.value) return
  try {
    const result = await ElMessageBox.prompt('输入新标题', '重命名会话', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: activeConversation.value.title,
    })
    const title = result.value.trim()
    if (!title) return
    const updated = await updateConversation(activeConversation.value.id!, { title })
    const idx = conversations.value.findIndex((c) => c.id === updated.id)
    if (idx >= 0) conversations.value[idx] = updated
  } catch {
    // 用户取消
  }
}

async function handleDelete() {
  if (!activeConversation.value) return
  try {
    await ElMessageBox.confirm('删除会话？此操作不可恢复。', '确认删除', { type: 'warning' })
    await deleteConversation(activeConversation.value.id!)
    conversations.value = conversations.value.filter((c) => c.id !== activeConversation.value!.id)
    activeId.value = null
    messages.value = []
  } catch {
    // 用户取消或删除被拒
  }
}

function handleSend() {
  const content = input.value.trim()
  const conv = activeConversation.value
  if (!content || !conv) return

  input.value = ''
  const clientMessageId = crypto.randomUUID()
  messages.value = [
    ...messages.value,
    {
      id: `local-${clientMessageId}`,
      conversationId: conv.id,
      role: 'USER',
      content,
      active: false,
    },
  ]

  const modelConfigId = selectedModelId.value ? Number(selectedModelId.value) : undefined

  activeController = streamSend(
    conv.id!,
    { clientMessageId, content, modelConfigId },
    (eventName, data) => dispatchSseEvent(eventName, data, stream.handlers),
    (err) => {
      stream.streamError.value = err instanceof Error ? err.message : '发送失败'
      stream.streaming.value = false
      // 断连/错误后对账最终状态
      void loadHistory(null)
    },
  )
}

function handleStop() {
  if (!activeConversation.value) return
  stopGeneration(activeConversation.value.id!)
  activeController?.abort()
  // 停止后对账：服务端会把该 attempt 标记 CANCELLED 并释放 slot
  void loadHistory(null)
}

function handleRegenerate(message: MessageResponse) {
  if (!activeConversation.value || !message.id) return
  activeController = streamRegenerate(
    activeConversation.value.id!,
    { modelConfigId: selectedModelId.value ? Number(selectedModelId.value) : undefined },
    (eventName, data) => dispatchSseEvent(eventName, data, stream.handlers),
    (err) => {
      stream.streamError.value = err instanceof Error ? err.message : '重新生成失败'
      stream.streaming.value = false
      void loadHistory(null)
    },
  )
}

watch(selectedModelId, async (newId, oldId) => {
  if (newId && newId !== oldId && activeConversation.value) {
    await updateConversation(activeConversation.value.id!, {
      defaultModelConfigId: Number(newId),
    })
  }
})
</script>

<template>
  <div class="chat-workspace">
    <div class="chat-sidebar">
      <el-button class="new-btn" type="primary" @click="handleCreate">新建会话</el-button>
      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === activeId }"
          @click="selectConversation(conv.id!)"
        >
          <div class="conv-title">{{ conv.title }}</div>
        </div>
        <div v-if="conversations.length === 0" class="conv-empty">还没有会话</div>
      </div>
    </div>

    <div class="chat-main">
      <template v-if="activeConversation">
        <div class="chat-header">
          <div class="header-title">{{ activeConversation.title }}</div>
          <div class="header-actions">
            <el-select
              v-model="selectedModelId"
              placeholder="选择模型"
              size="small"
              style="width: 180px"
            >
              <el-option v-for="m in models" :key="m.id" :label="m.displayName" :value="m.id" />
            </el-select>
            <el-button size="small" @click="handleRename">重命名</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete">删除</el-button>
          </div>
        </div>

        <div class="chat-body">
          <div v-if="loadingHistory" class="history-loading">加载中…</div>
          <el-button
            v-if="hasMoreHistory && !loadingHistory"
            class="load-more"
            size="small"
            text
            @click="loadHistory(nextBefore)"
          >
            加载更早的消息
          </el-button>

          <div
            v-for="msg in messages"
            :key="msg.id"
            class="message"
            :class="msg.role?.toLowerCase()"
          >
            <div class="msg-role">
              {{ msg.role === 'USER' ? '你' : 'AI' }}
              <span v-if="msg.modelName" class="msg-model">{{ msg.modelName }}</span>
            </div>
            <div class="msg-content">{{ msg.content }}</div>
            <MessageSourcesPanel
              v-if="msg.role === 'ASSISTANT'"
              :rag-status="msg.ragStatus"
              :sources="msg.sources"
            />
            <div v-if="msg.generationStatus === 'FAILED'" class="msg-error">
              {{ msg.errorCode ?? '生成失败' }}
            </div>
            <div v-if="msg.generationStatus === 'CANCELLED'" class="msg-error">已取消</div>
            <div v-if="msg.role === 'ASSISTANT'" class="msg-actions">
              <el-button
                v-if="msg.generationStatus === 'GENERATING'"
                size="small"
                type="warning"
                plain
                @click="handleStop"
              >
                停止
              </el-button>
              <el-button size="small" text @click="handleRegenerate(msg)">重新生成</el-button>
            </div>
          </div>
          <div v-if="stream.streamError.value" class="stream-error">
            {{ stream.streamError.value }}
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="input"
            type="textarea"
            :rows="3"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="input-actions">
            <el-button type="primary" :disabled="!canSend" @click="handleSend">发送</el-button>
          </div>
        </div>
      </template>

      <div v-else class="chat-empty">
        <p>选择一个会话或新建会话开始聊天</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-workspace {
  display: flex;
  height: calc(100vh - 60px);
}
.chat-sidebar {
  width: 240px;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  padding: 12px;
}
.new-btn {
  margin-bottom: 12px;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
}
.conv-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
}
.conv-item:hover {
  background: #f5f7fa;
}
.conv-item.active {
  background: #ecf5ff;
}
.conv-title {
  font-size: 0.9rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-empty {
  color: #999;
  text-align: center;
  padding: 20px 0;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #eee;
}
.header-title {
  font-weight: 600;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.message {
  margin-bottom: 16px;
  max-width: 78%;
}
.message.user {
  margin-left: auto;
  text-align: right;
}
.message.assistant {
  margin-right: auto;
  text-align: left;
}
.msg-role {
  font-size: 0.8rem;
  color: #666;
  margin-bottom: 4px;
}
.msg-model {
  color: #999;
  margin-left: 6px;
  font-size: 0.75rem;
}
.msg-content {
  background: #f5f7fa;
  padding: 10px 12px;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}
.message.user .msg-content {
  background: #ecf5ff;
}
.msg-error {
  color: #f56c6c;
  font-size: 0.8rem;
  margin-top: 4px;
}
.msg-actions {
  margin-top: 6px;
}
.stream-error {
  color: #f56c6c;
  margin-top: 8px;
}
.history-loading {
  color: #999;
  text-align: center;
  padding: 8px;
}
.load-more {
  margin-bottom: 8px;
}
.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}
.chat-input {
  padding: 12px 16px;
  border-top: 1px solid #eee;
}
.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
