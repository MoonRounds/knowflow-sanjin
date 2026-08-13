// tags API 客户端测试：owner 级标签列表请求。
import { afterEach, describe, expect, it, vi } from 'vitest'
import { listTags } from '../tags'

describe('tags api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists owner tags', async () => {
    const tags = [{ id: '1', name: 'java' }]
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue(tags) }),
    )
    await expect(listTags()).resolves.toEqual(tags)
    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/v1/tags', expect.anything())
  })
})
