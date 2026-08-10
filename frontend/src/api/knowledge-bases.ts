import type {
  CreateKnowledgeBaseRequest,
  KnowledgeBaseResponse,
  UpdateKnowledgeBaseRequest,
} from './types/knowledge-base'
import { request } from './request'

export { ApiError } from './request'

export function listKnowledgeBases(): Promise<KnowledgeBaseResponse[]> {
  return request('/knowledge-bases')
}

export function getKnowledgeBase(id: string): Promise<KnowledgeBaseResponse> {
  return request(`/knowledge-bases/${id}`)
}

export function createKnowledgeBase(
  payload: CreateKnowledgeBaseRequest,
): Promise<KnowledgeBaseResponse> {
  return request('/knowledge-bases', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateKnowledgeBase(
  id: string,
  payload: UpdateKnowledgeBaseRequest,
): Promise<KnowledgeBaseResponse> {
  return request(`/knowledge-bases/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteKnowledgeBase(id: string, rowVersion: number): Promise<void> {
  return request(`/knowledge-bases/${id}`, {
    method: 'DELETE',
    headers: versionHeaders(rowVersion),
  })
}

export function disableKnowledgeBase(id: string, rowVersion: number): Promise<void> {
  return request(`/knowledge-bases/${id}/disable`, {
    method: 'PUT',
    headers: versionHeaders(rowVersion),
  })
}

export function enableKnowledgeBase(id: string, rowVersion: number): Promise<void> {
  return request(`/knowledge-bases/${id}/enable`, {
    method: 'PUT',
    headers: versionHeaders(rowVersion),
  })
}

function versionHeaders(rowVersion: number): HeadersInit {
  return {
    'Content-Type': 'application/json',
    'If-Match': `"${rowVersion}"`,
  }
}
