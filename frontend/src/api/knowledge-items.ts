/**
 * KnowledgeItem API 客户端：Manual Note 创建/查看/编辑/删除。
 *
 * <p>写操作通过 If-Match 头传递 rowVersion，与后端乐观锁对应；版本冲突时后端返回 409。
 * knowledgeBaseIds 至少一个，为空时后端返回 422。
 */
import type {
  CreateManualNoteRequest,
  KnowledgeItemResponse,
  UpdateManualNoteRequest,
} from './types/knowledge-item'
import { request } from './request'

export { ApiError } from './request'

export function listKnowledgeItems(): Promise<KnowledgeItemResponse[]> {
  return request('/knowledge-items')
}

export function getKnowledgeItem(id: string): Promise<KnowledgeItemResponse> {
  return request(`/knowledge-items/${id}`)
}

export function createKnowledgeItem(
  payload: CreateManualNoteRequest,
): Promise<KnowledgeItemResponse> {
  return request('/knowledge-items', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateKnowledgeItem(
  id: string,
  payload: UpdateManualNoteRequest,
): Promise<KnowledgeItemResponse> {
  return request(`/knowledge-items/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteKnowledgeItem(id: string, rowVersion: number): Promise<void> {
  return request(`/knowledge-items/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      'If-Match': `"${rowVersion}"`,
    },
  })
}
