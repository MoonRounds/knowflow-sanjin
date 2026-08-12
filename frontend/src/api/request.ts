const API_BASE = import.meta.env.VITE_API_BASE ?? '/api/v1'

/** 后端 Problem Details 错误：携带稳定 errorCode 与 correlationId，供 UI 展示稳定错误信息。 */
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

/** 解析 Problem Details 响应体为 ApiError；非 JSON 错误体退化为状态码消息。 */
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

/** 统一 fetch 封装：JSON 序列化、Problem Details 解析、204 空响应、超时兜底。 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // RequestInit.headers 不能整体覆盖 JSON 默认头；If-Match 等调用方头部必须与
  // Content-Type 合并，否则带 JSON body 的乐观锁请求会被浏览器作为 text/plain 发送。
  const headers = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string> | undefined),
  }
  // 慢后端挂起时兜底中断，避免 loading 永久卡死；调用方自定义 signal 通过 AbortSignal.any 合并。
  const timeoutSignal = AbortSignal.timeout(60_000)
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
    signal: init?.signal ? AbortSignal.any([init.signal, timeoutSignal]) : timeoutSignal,
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
