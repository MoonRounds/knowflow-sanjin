/**
 * 会话知识提取 API 客户端：触发提取、查询候选、编辑草稿、拒绝/恢复/确认。
 */
import type { components } from './types/generated'
import { request } from './request'

export { ApiError } from './request'

export type ExtractionTaskResponse = components['schemas']['ExtractionTaskResponse']
export type CandidateResponse = components['schemas']['CandidateResponse']
export type CandidatePageResponse = components['schemas']['CandidatePageResponse']

export function triggerExtraction(conversationId: string): Promise<ExtractionTaskResponse> {
  return request(`/conversations/${conversationId}/extraction`, { method: 'POST' })
}

export function listCandidates(params: {
  status?: string
  page?: number
  size?: number
}): Promise<CandidatePageResponse> {
  const query = new URLSearchParams()
  if (params.status) query.set('status', params.status)
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  const qs = query.toString()
  return request(`/candidates${qs ? `?${qs}` : ''}`)
}

export function getCandidate(id: string): Promise<CandidateResponse> {
  return request(`/candidates/${id}`)
}

export function updateCandidateDraft(
  id: string,
  body: {
    title: string
    summary?: string
    content: string
    knowledgeBaseId: string
    tags?: string[]
  },
  ifMatch: string,
): Promise<CandidateResponse> {
  return request(`/candidates/${id}/draft`, {
    method: 'PUT',
    headers: { 'If-Match': ifMatch },
    body: JSON.stringify(body),
  })
}

export function rejectCandidate(id: string, ifMatch: string): Promise<CandidateResponse> {
  return request(`/candidates/${id}/reject`, {
    method: 'POST',
    headers: { 'If-Match': ifMatch },
  })
}

export function restoreCandidate(id: string, ifMatch: string): Promise<CandidateResponse> {
  return request(`/candidates/${id}/restore`, {
    method: 'POST',
    headers: { 'If-Match': ifMatch },
  })
}

export function confirmCandidate(id: string): Promise<CandidateResponse> {
  return request(`/candidates/${id}/confirm`, { method: 'POST' })
}
