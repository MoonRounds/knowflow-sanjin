import { ApiError } from '../api/request'

/**
 * 统一的错误展示文案：优先稳定中文 errorCode，其次 Problem Details 的 detail，最后调用方 fallback。
 *
 * <p>后端 errorCode 已是中文（如「知识库不存在」），detail 可能携带 id 等补充信息；
 * 优先 errorCode 保证用户看到的始终是中文，detail 仅在 errorCode 缺失时兜底。
 */
export function errorText(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    return e.errorCode || e.message || fallback
  }
  return e instanceof Error ? e.message : fallback
}

/** fetch 层网络错误（如浏览器 `Failed to fetch`）统一映射为中文提示。 */
export function networkErrorMessage(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    return e.errorCode || e.message || fallback
  }
  if (e instanceof TypeError) {
    return '无法连接服务器，请检查后端是否已启动'
  }
  return e instanceof Error ? e.message : fallback
}
