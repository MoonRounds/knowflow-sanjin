// 会话/消息 API 类型：从 OpenAPI 生成的 generated.ts 再导出，保持与后端契约同步。
import type { components, operations } from './generated'

export type ConversationResponse = components['schemas']['ConversationResponse']
export type CreateConversationRequest = components['schemas']['CreateConversationRequest']
export type UpdateConversationRequest = components['schemas']['UpdateConversationRequest']
export type MessageResponse = components['schemas']['MessageResponse']
export type RetrievedSource = components['schemas']['RetrievedSource']
export type MessagePageResponse = components['schemas']['MessagePageResponse']
export type SendMessageRequest = components['schemas']['SendMessageRequest']
export type RegenerateRequest = components['schemas']['RegenerateRequest']
export type TokenUsage = components['schemas']['TokenUsage']
export type SseEmitter = components['schemas']['SseEmitter']

export type SendMessagesResponse =
  operations['send']['responses']['200']['content']['text/event-stream']
