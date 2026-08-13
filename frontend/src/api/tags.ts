/**
 * Tag API 客户端：owner 级标签列表，供文档列表 Tag 过滤下拉使用（G23）。
 */
import type { TagResponse } from './types/tag'
import { request } from './request'

export function listTags(): Promise<TagResponse[]> {
  return request('/tags')
}
