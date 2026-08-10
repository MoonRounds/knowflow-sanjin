// 健康检查：用于前端启动时探测后端是否可达。
const API_BASE = import.meta.env.VITE_API_BASE ?? '/api/v1'

export interface HealthStatus {
  status: string
  service: string
  timestamp: string
}

export async function fetchHealth(): Promise<HealthStatus> {
  const response = await fetch(`${API_BASE}/health`)
  if (!response.ok) {
    throw new Error(`Health check failed: ${response.status}`)
  }
  return response.json()
}
