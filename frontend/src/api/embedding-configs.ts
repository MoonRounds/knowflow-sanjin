/**
 * 系统级向量模型配置 API 客户端：读取当前配置、保存（保存前服务端重测）与向量化预检。
 *
 * <p>API Key 只从后端获取掩码，不在此保存明文；POST /test 会真实调用一次云端向量化。
 */
import type {
  EmbeddingConfigResponse,
  EmbeddingVectorizeTestResult,
  TestEmbeddingConfigRequest,
  UpdateEmbeddingConfigRequest,
} from './types/embedding-config'
import { request } from './request'

export function getEmbeddingConfig(): Promise<EmbeddingConfigResponse> {
  return request('/embedding-config')
}

export function updateEmbeddingConfig(
  payload: UpdateEmbeddingConfigRequest,
): Promise<EmbeddingConfigResponse> {
  return request('/embedding-config', { method: 'PUT', body: JSON.stringify(payload) })
}

/** 用候选配置真实调用一次向量化，成功返回探测到的维度（不持久化）。 */
export function testEmbeddingConfig(
  payload: TestEmbeddingConfigRequest,
): Promise<EmbeddingVectorizeTestResult> {
  return request('/embedding-config/test', { method: 'POST', body: JSON.stringify(payload) })
}
