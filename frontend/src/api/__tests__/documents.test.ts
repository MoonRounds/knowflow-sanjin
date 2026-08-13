// documents API 客户端测试：分页/过滤参数序列化、CRUD 方法与 If-Match。
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  createDocument,
  deleteDocument,
  getDocument,
  listDocuments,
  updateDocument,
} from '../documents'
import type { DocumentPageResponse } from '../types/document'

const page: DocumentPageResponse = {
  items: [
    {
      id: '1',
      sourceType: 'MANUAL_NOTE',
      title: 'My Note',
      contentVersion: 1,
      indexStatus: 'INDEXED',
      knowledgeBaseId: '2',
      tags: ['java'],
      rowVersion: 0,
      createdAt: '2026-08-09T00:00:00Z',
      updatedAt: '2026-08-09T00:00:00Z',
    },
  ],
  page: 1,
  size: 20,
  total: 1,
}

describe('documents api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists documents without query string when no params given', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(page) }),
    )
    await expect(listDocuments()).resolves.toEqual(page)
    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/documents', expect.anything())
  })

  it('serializes filter and pagination params', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(page) }),
    )
    await listDocuments({
      knowledgeBaseId: '2',
      sourceType: 'MANUAL_NOTE',
      tag: 'java',
      indexStatus: 'PENDING',
      page: 3,
      size: 10,
    })
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      '/api/v1/documents?knowledgeBaseId=2&sourceType=MANUAL_NOTE&tag=java&indexStatus=PENDING&page=3&size=10',
      expect.anything(),
    )
  })

  it('skips empty params in the query string', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(page) }),
    )
    await listDocuments({ knowledgeBaseId: '2', sourceType: undefined, page: 2 })
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      '/api/v1/documents?knowledgeBaseId=2&page=2',
      expect.anything(),
    )
  })

  it('gets a single document', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(page.items![0]) }),
    )
    await expect(getDocument('1')).resolves.toEqual(page.items![0])
    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/documents/1', expect.anything())
  })

  it('creates a document with a POST', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(page.items![0]) }),
    )
    await createDocument({ content: '# hi', knowledgeBaseId: '2', tags: ['java'] })
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/v1/documents')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({
      content: '# hi',
      knowledgeBaseId: '2',
      tags: ['java'],
    })
  })

  it('updates with rowVersion in body', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(page.items![0]) }),
    )
    await updateDocument('1', { content: '# v2', knowledgeBaseId: '2', rowVersion: 1 })
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/v1/documents/1')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({
      content: '# v2',
      knowledgeBaseId: '2',
      rowVersion: 1,
    })
  })

  it('deletes with If-Match header', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 204 }))
    await deleteDocument('1', 2)
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      '/api/v1/documents/1',
      expect.objectContaining({
        method: 'DELETE',
        headers: expect.objectContaining({ 'If-Match': '"2"' }),
      }),
    )
  })

  it('throws ApiError on non-ok responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 422,
        json: vi.fn().mockResolvedValue({
          errorCode: '知识库不存在',
          detail: 'Unknown knowledge base',
          correlationId: 'corr-1',
        }),
      }),
    )
    const err = await createDocument({ content: '# hi', knowledgeBaseId: '999' }).catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(422)
    expect(err.errorCode).toBe('知识库不存在')
  })
})
