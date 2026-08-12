<script setup lang="ts">
// 单条聊天消息：computed 缓存 Markdown 渲染，避免每条 delta 到达时全量重渲染历史消息。
import { computed } from 'vue'
import type { MessageResponse } from '../api/types/conversation'
import type { RouterDiagnostic } from '../composables/useChatStream'
import { escapeHtml, renderMarkdown } from '../utils/markdown'
import { routerBadgeText } from '../utils/rag'
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

const routeBadge = computed(() => routerBadgeText(props.router ?? undefined, props.msg.ragStatus))
</script>

<template>
  <div class="message" :class="msg.role?.toLowerCase()">
    <div class="msg-role">
      {{ msg.role === 'USER' ? '你' : 'AI' }}
      <span v-if="msg.modelName" class="msg-model">{{ msg.modelName }}</span>
    </div>
    <div class="msg-content" v-html="contentHtml" />
    <div v-if="msg.role === 'ASSISTANT' && routeBadge" class="route-badge">{{ routeBadge }}</div>
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
        @click="emit('stop')"
      >
        停止
      </el-button>
      <el-button size="small" text @click="emit('regenerate', msg)">重新生成</el-button>
    </div>
  </div>
</template>

<style scoped>
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
  word-break: break-word;
  line-height: 1.7;
  text-align: left;
}
.message.user .msg-content {
  background: #ecf5ff;
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
  background: #e7eaee;
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
  border-left: 3px solid #e4e7ed;
  margin: 0.6em 0;
  padding-left: 12px;
  color: #888;
}
.msg-content :deep(table) {
  border-collapse: collapse;
  margin: 0.6em 0;
}
.msg-content :deep(th),
.msg-content :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 5px 9px;
  font-size: 0.9em;
}
.msg-content :deep(a) {
  color: #409eff;
}
.msg-error {
  color: #f56c6c;
  font-size: 0.8rem;
  margin-top: 4px;
}
.msg-actions {
  margin-top: 6px;
}
.route-badge {
  display: inline-block;
  margin-top: 6px;
  font-size: 0.75rem;
  color: #67c23a;
  background: #f0f9eb;
  border: 1px solid #b3e19d;
  border-radius: 999px;
  padding: 2px 8px;
}
</style>
