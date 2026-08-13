/**
 * Document API 客户端：Manual Note 创建/查看/编辑/删除 + 分页过滤列表。
 *
 * <p>写操作通过 If-Match 头传递 rowVersion，与后端乐观锁对应；版本冲突时后端返回 409。
 * knowledgeBaseId 必填，为空时后端返回 422。
 */
import type {
  CreateDocumentRequest,
  DocumentPageResponse,
  KnowledgeDocumentResponse,
  UpdateDocumentRequest,
} from './types/document'
import { request } from './request'

export { ApiError } from './request'

/** 文档列表查询参数：按库 + sourceType/tag/indexStatus 过滤 + 分页（G22）。 */
export interface DocumentListQuery {
  knowledgeBaseId?: string
  sourceType?: string
  tag?: string
  indexStatus?: string
  page?: number
  size?: number
}

export function listDocuments(query?: DocumentListQuery): Promise<DocumentPageResponse> {
  const params = new URLSearchParams()
  if (query?.knowledgeBaseId) params.set('knowledgeBaseId', query.knowledgeBaseId)
  if (query?.sourceType) params.set('sourceType', query.sourceType)
  if (query?.tag) params.set('tag', query.tag)
  if (query?.indexStatus) params.set('indexStatus', query.indexStatus)
  if (query?.page != null) params.set('page', String(query.page))
  if (query?.size != null) params.set('size', String(query.size))
  const qs = params.toString()
  return request(qs ? `/documents?${qs}` : '/documents')
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
