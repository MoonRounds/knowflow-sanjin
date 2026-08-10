import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchHealth, type HealthStatus } from '../health'

describe('fetchHealth', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns the backend health payload', async () => {
    const payload: HealthStatus = {
      status: 'UP',
      service: 'knowflow-app',
      timestamp: '2026-08-09T00:00:00Z',
    }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue(payload),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchHealth()).resolves.toEqual(payload)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/health')
  })

  it('reports a non-success response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
      }),
    )

    await expect(fetchHealth()).rejects.toThrow('Health check failed: 503')
  })
})
