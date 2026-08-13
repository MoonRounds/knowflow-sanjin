/**
 * 文档上传 API 客户端：上传 Markdown/TXT 文件、查询文件元数据、下载原文件。
 *
 * <p>上传使用 multipart/form-data；knowledgeBaseId 为单值必填。
 */
import type { components } from './types/generated'
import { request, API_BASE, parseProblem } from './request'

export { ApiError } from './request'

export type FileUploadResponse = components['schemas']['FileUploadResponse']
export type FileMetadataResponse = components['schemas']['FileMetadataResponse']

export async function uploadFile(file: File, knowledgeBaseId: string): Promise<FileUploadResponse> {
  const form = new FormData()
  form.append('file', file)
  form.append('knowledgeBaseId', knowledgeBaseId)
  const response = await fetch(`${API_BASE}/files`, {
    method: 'POST',
    body: form,
  })
  if (!response.ok) {
    throw await parseProblem(response)
  }
  return response.json() as Promise<FileUploadResponse>
}

export function getFileMetadata(id: string): Promise<FileMetadataResponse> {
  return request(`/files/${id}`)
}

export function getFileMetadataByDocument(
  documentId: string,
): Promise<FileMetadataResponse | null> {
  return request(`/documents/${documentId}/file`)
}

export function downloadFileUrl(id: string): string {
  return `${API_BASE}/files/${id}/download`
}
