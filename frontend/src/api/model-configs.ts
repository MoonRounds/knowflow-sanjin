import type {
  ConnectionTestResult,
  CreateModelConfigRequest,
  ModelConfigResponse,
  ModelConfigRevisionResponse,
  OwnerAiSettingsResponse,
  UpdateModelConfigRequest,
  UpdateOwnerAiSettingsRequest,
  UtilityCapabilityTestResult,
} from './types/model-config'
import { request } from './request'

export { ApiError } from './request'

export function listModelConfigs(): Promise<ModelConfigResponse[]> {
  return request('/model-configs')
}

export function getModelConfig(id: string): Promise<ModelConfigResponse> {
  return request(`/model-configs/${id}`)
}

export function createModelConfig(payload: CreateModelConfigRequest): Promise<ModelConfigResponse> {
  return request('/model-configs', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateModelConfig(
  id: string,
  payload: UpdateModelConfigRequest,
): Promise<ModelConfigResponse> {
  return request(`/model-configs/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function deleteModelConfig(id: string): Promise<void> {
  return request(`/model-configs/${id}`, { method: 'DELETE' })
}

export function disableModelConfig(id: string): Promise<void> {
  return request(`/model-configs/${id}/disable`, { method: 'PUT' })
}

export function enableModelConfig(id: string): Promise<void> {
  return request(`/model-configs/${id}/enable`, { method: 'PUT' })
}

export function listRevisions(id: string): Promise<ModelConfigRevisionResponse[]> {
  return request(`/model-configs/${id}/revisions`)
}

export function testConnection(id: string): Promise<ConnectionTestResult> {
  return request(`/model-configs/${id}/test-connection`, { method: 'POST' })
}

export function testUtilityCapability(id: string): Promise<UtilityCapabilityTestResult> {
  return request(`/model-configs/${id}/test-utility-capability`, { method: 'POST' })
}

export function getOwnerAiSettings(): Promise<OwnerAiSettingsResponse> {
  return request('/owner-ai-settings')
}

export function updateOwnerAiSettings(
  payload: UpdateOwnerAiSettingsRequest,
): Promise<OwnerAiSettingsResponse> {
  return request('/owner-ai-settings', { method: 'PUT', body: JSON.stringify(payload) })
}
