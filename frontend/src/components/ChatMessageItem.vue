<script setup lang="ts">
// 单条聊天消息：computed 缓存 Markdown 渲染，避免每条 delta 到达时全量重渲染历史消息。
import { computed } from 'vue'
import type { MessageResponse } from '../api/types/conversation'
import type { RouterDiagnostic } from '../composables/useChatStream'
import { escapeHtml, renderMarkdown } from '../utils/markdown'
import MessageSourcesPanel from './MessageSourcesPanel.vue'

const props = defineProps<{
  msg: MessageResponse
  router?: RouterDiagnostic | null
}>()

const emit = defineEmits<{ stop: []; regenerate: [msg: MessageResponse] }>()

/** 按消息内容缓存渲染：同一条消息的 content 未变则不重新解析 markdown。 */
const contentHtml = computed(() =>
  props.msg.role === 'USER'
    ? escapeHtml(props.msg.content ?? '')
    : renderMarkdown(props.msg.content ?? ''),
)
</script>

<template>
  <div class="message" :class="msg.role?.toLowerCase()">
    <div class="msg-role">
      {{ msg.role === 'USER' ? '你' : 'AI' }}
      <span v-if="msg.modelName" class="msg-model">{{ msg.modelName }}</span>
      <span
        v-if="msg.role === 'ASSISTANT' && msg.generationStatus === 'GENERATING'"
        class="msg-status"
      >
        生成中…
      </span>
    </div>
    <div
      class="msg-content"
      :aria-live="msg.generationStatus === 'GENERATING' ? 'polite' : undefined"
      v-html="contentHtml"
    />
    <div v-if="msg.generationStatus === 'FAILED'" class="msg-error">
      {{ msg.errorCode ?? '生成失败' }}
    </div>
    <div v-if="msg.generationStatus === 'CANCELLED'" class="msg-error">已取消</div>
    <div
      v-if="msg.role === 'ASSISTANT' && msg.generationStatus !== 'GENERATING'"
      class="message-footer"
    >
      <MessageSourcesPanel :rag-status="msg.ragStatus" :sources="msg.sources" />
      <el-button class="regenerate-action" size="small" text @click="emit('regenerate', msg)">
        重新生成
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.message {
  margin-bottom: 16px;
  max-width: 78%;
}
.message.user {
  width: fit-content;
  max-width: 72%;
  margin-left: auto;
  text-align: right;
}
.message.assistant {
  width: fit-content;
  max-width: 78%;
  margin-right: auto;
  text-align: left;
}
.msg-role {
  font-size: 0.75rem;
  font-weight: 900;
  color: var(--kf-muted);
  margin-bottom: 4px;
}
.msg-model {
  color: var(--kf-muted);
  margin-left: 6px;
  font-size: 0.7rem;
  font-weight: 700;
  border: 1px solid var(--kf-line);
  border-radius: 999px;
  padding: 1px 7px;
  background: var(--kf-paper);
}
.msg-status {
  margin-left: 6px;
  font-size: 0.7rem;
  font-weight: 900;
  color: var(--kf-hot);
}
.msg-content {
  background: var(--kf-paper-3);
  border: 1px solid var(--kf-line);
  color: var(--kf-ink);
  padding: 12px 15px;
  border-radius: 12px;
  word-break: break-word;
  line-height: 1.7;
  text-align: left;
}
.message.user .msg-content {
  background: var(--kf-ink);
  border-color: var(--kf-ink);
  color: var(--kf-paper);
  border-radius: 16px;
  border-top-right-radius: 6px;
  white-space: pre-wrap;
}
.msg-content :deep(p) {
  margin: 0.5em 0;
}
.msg-content :deep(p:first-child) {
  margin-top: 0;
}
.msg-content :deep(p:last-child) {
  margin-bottom: 0;
}
.msg-content :deep(pre) {
  background: #20221e;
  color: #f2f0e9;
  border-radius: 6px;
  padding: 10px 12px;
  overflow-x: auto;
  margin: 0.6em 0;
}
.msg-content :deep(code) {
  background: var(--kf-paper);
  border: 1px solid var(--kf-line);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 0.9em;
}
.msg-content :deep(pre code) {
  background: transparent;
  padding: 0;
}
.msg-content :deep(ul),
.msg-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.4em;
}
.msg-content :deep(blockquote) {
  border-left: 3px solid var(--kf-line);
  margin: 0.6em 0;
  padding-left: 12px;
  color: var(--kf-muted);
}
.msg-content :deep(table) {
  border-collapse: collapse;
  margin: 0.6em 0;
}
.msg-content :deep(th),
.msg-content :deep(td) {
  border: 1px solid var(--kf-line);
  padding: 5px 9px;
  font-size: 0.9em;
}
.msg-content :deep(a) {
  color: var(--kf-green);
  font-weight: 800;
}
.msg-error {
  color: #f56c6c;
  font-size: 0.8rem;
  margin-top: 4px;
}
.message-footer {
  margin-top: 6px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.message-footer :deep(.el-button) {
  border-radius: 10px;
  font-weight: 800;
}
.regenerate-action {
  flex: 0 0 auto;
  margin-left: auto;
}
</style>
