// knowledge-bases API 客户端测试：请求方法/URL/头部（If-Match）与 Problem Details 解析。
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  createKnowledgeBase,
  deleteKnowledgeBase,
  disableKnowledgeBase,
  enableKnowledgeBase,
  getKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase,
} from '../knowledge-bases'
import type { KnowledgeBaseResponse } from '../types/knowledge-base'

const kb: KnowledgeBaseResponse = {
  id: '1',
  name: 'Java',
  description: 'Java notes',
  enabled: true,
  rowVersion: 0,
  createdAt: '2026-08-09T00:00:00Z',
  updatedAt: '2026-08-09T00:00:00Z',
}

describe('knowledge-bases api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists knowledge bases', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue([kb]) }),
    )
    await expect(listKnowledgeBases()).resolves.toEqual([kb])
  })

  it('gets a single knowledge base', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(kb) }),
    )
    await expect(getKnowledgeBase('1')).resolves.toEqual(kb)
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      '/api/v1/knowledge-bases/1',
      expect.objectContaining({ headers: { 'Content-Type': 'application/json' } }),
    )
  })

  it('creates a knowledge base with a POST', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(kb) }),
    )
    await createKnowledgeBase({ name: 'Java' })
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/v1/knowledge-bases')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({ name: 'Java' })
  })

  it('updates with rowVersion passthrough', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(kb) }),
    )
    await updateKnowledgeBase('1', { name: 'Java Deep', rowVersion: 3 })
    const [url, init] = vi.mocked(fetch).mock.calls[0]
    expect(url).toBe('/api/v1/knowledge-bases/1')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(init?.body as string)).toEqual({ name: 'Java Deep', rowVersion: 3 })
  })

  it('disables and enables', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 204 }))
    await disableKnowledgeBase('1', 3)
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      '/api/v1/knowledge-bases/1/disable',
      expect.objectContaining({
        method: 'PUT',
        headers: expect.objectContaining({ 'If-Match': '"3"' }),
      }),
    )
    await enableKnowledgeBase('1', 4)
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      '/api/v1/knowledge-bases/1/enable',
      expect.objectContaining({
        method: 'PUT',
        headers: expect.objectContaining({ 'If-Match': '"4"' }),
      }),
    )
  })

  it('deletes', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 204 }))
    await deleteKnowledgeBase('1', 5)
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      '/api/v1/knowledge-bases/1',
      expect.objectContaining({
        method: 'DELETE',
        headers: expect.objectContaining({ 'If-Match': '"5"' }),
      }),
    )
  })

  it('throws ApiError with problem details on conflict', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: vi.fn().mockResolvedValue({
          errorCode: '知识库名称冲突',
          detail: 'A KnowledgeBase with this name already exists.',
          correlationId: 'abc-123',
        }),
      }),
    )
    const err = await createKnowledgeBase({ name: 'Java' }).catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(409)
    expect(err.errorCode).toBe('知识库名称冲突')
    expect(err.correlationId).toBe('abc-123')
  })
})
