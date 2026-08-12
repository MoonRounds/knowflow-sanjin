import { afterEach, describe, expect, it, vi } from 'vitest'
import { request } from '../request'

describe('request', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('merges caller headers with the JSON content type', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: vi.fn().mockResolvedValue({}) }),
    )

    await request('/candidate', {
      method: 'PUT',
      headers: { 'If-Match': '"3"' },
      body: JSON.stringify({ title: 'edited' }),
    })

    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      '/api/v1/candidate',
      expect.objectContaining({
        headers: {
          'Content-Type': 'application/json',
          'If-Match': '"3"',
        },
      }),
    )
  })
})
