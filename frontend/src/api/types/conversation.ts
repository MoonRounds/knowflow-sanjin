import type { components, operations } from './generated'

export type ConversationResponse = components['schemas']['ConversationResponse']
export type CreateConversationRequest = components['schemas']['CreateConversationRequest']
export type UpdateConversationRequest = components['schemas']['UpdateConversationRequest']
export type MessageResponse = components['schemas']['MessageResponse']
export type MessagePageResponse = components['schemas']['MessagePageResponse']
export type SendMessageRequest = components['schemas']['SendMessageRequest']
export type RegenerateRequest = components['schemas']['RegenerateRequest']
export type TokenUsage = components['schemas']['TokenUsage']
export type SseEmitter = components['schemas']['SseEmitter']

export type SendMessagesResponse =
  operations['send']['responses']['200']['content']['text/event-stream']
