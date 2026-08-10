/**
 * Processing 任务 API 客户端：轻量列表查询与失败任务手动 Retry。
 */
import type { ProcessingTaskResponse } from './types/processing-task'
import { request } from './request'

export { ApiError } from './request'

export function listProcessingTasks(status?: string): Promise<ProcessingTaskResponse[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return request(`/processing-tasks${query}`)
}

export function retryProcessingTask(id: string): Promise<ProcessingTaskResponse> {
  return request(`/processing-tasks/${id}/retry`, {
    method: 'POST',
  })
}
