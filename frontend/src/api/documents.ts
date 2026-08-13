/**
 * Document API 客户端：Manual Note 创建/查看/编辑/删除。
 *
 * <p>写操作通过 If-Match 头传递 rowVersion，与后端乐观锁对应；版本冲突时后端返回 409。
 * knowledgeBaseId 必填，为空时后端返回 422。
 */
import type {
  CreateDocumentRequest,
  KnowledgeDocumentResponse,
  UpdateDocumentRequest,
} from './types/document'
import { request } from './request'

export { ApiError } from './request'

export function listDocuments(): Promise<KnowledgeDocumentResponse[]> {
  return request('/documents')
}

export function getDocument(id: string): Promise<KnowledgeDocumentResponse> {
  return request(`/documents/${id}`)
}

export function createDocument(payload: CreateDocumentRequest): Promise<KnowledgeDocumentResponse> {
  return request('/documents', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateDocument(
  id: string,
  payload: UpdateDocumentRequest,
): Promise<KnowledgeDocumentResponse> {
  return request(`/documents/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteDocument(id: string, rowVersion: number): Promise<void> {
  return request(`/documents/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      'If-Match': `"${rowVersion}"`,
    },
  })
}
