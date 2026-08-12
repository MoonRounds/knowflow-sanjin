// errorText 工具测试：优先中文 errorCode、detail 兜底、fetch 网络错误中文映射。
import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api/request'
import { errorText, networkErrorMessage } from '../errorText'

describe('errorText', () => {
  it('prefers Chinese errorCode over detail', () => {
    const err = new ApiError(404, 'KnowledgeBase not found', '知识库不存在', 'corr-1')
    expect(errorText(err, '加载失败')).toBe('知识库不存在')
  })

  it('falls back to detail when errorCode is missing', () => {
    const err = new ApiError(409, '名称冲突', undefined, 'corr-2')
    expect(errorText(err, '加载失败')).toBe('名称冲突')
  })

  it('falls back to fallback for plain Error', () => {
    expect(errorText(new Error('boom'), '加载失败')).toBe('boom')
    expect(errorText('unknown', '加载失败')).toBe('加载失败')
  })
})

describe('networkErrorMessage', () => {
  it('maps TypeError (Failed to fetch) to Chinese', () => {
    expect(networkErrorMessage(new TypeError('Failed to fetch'), '发送失败')).toBe(
      '无法连接服务器，请检查后端是否已启动',
    )
  })

  it('prefers Chinese errorCode for ApiError', () => {
    const err = new ApiError(500, 'Internal server error', '内部错误', 'corr-3')
    expect(networkErrorMessage(err, '发送失败')).toBe('内部错误')
  })

  it('falls back for unknown error', () => {
    expect(networkErrorMessage('oops', '发送失败')).toBe('发送失败')
  })
})
