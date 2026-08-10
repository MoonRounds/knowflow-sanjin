const API_BASE = import.meta.env.VITE_API_BASE ?? '/api/v1'

export class ApiError extends Error {
  readonly status: number
  readonly errorCode?: string
  readonly correlationId?: string

  constructor(status: number, message: string, errorCode?: string, correlationId?: string) {
    super(message)
    this.status = status
    this.errorCode = errorCode
    this.correlationId = correlationId
  }
}

export async function parseProblem(response: Response): Promise<ApiError> {
  let message = `Request failed: ${response.status}`
  let errorCode: string | undefined
  let correlationId: string | undefined
  try {
    const body = await response.json()
    if (typeof body.detail === 'string') message = body.detail
    if (typeof body.errorCode === 'string') errorCode = body.errorCode
    if (typeof body.correlationId === 'string') correlationId = body.correlationId
  } catch {
    // ignore non-JSON error bodies
  }
  return new ApiError(response.status, message, errorCode, correlationId)
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!response.ok) {
    throw await parseProblem(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export { API_BASE }
