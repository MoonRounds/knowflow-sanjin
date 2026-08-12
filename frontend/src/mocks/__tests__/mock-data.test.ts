// 集中 mock 数据测试：校验 mock-data.ts 的契约结构（标题非空、进度合法、候选/域/记忆齐全）。
import { describe, expect, it } from 'vitest'
import { mockFlowData } from '../mock-data'

describe('mockFlowData', () => {
  it('exposes a focus section with a title, description and metrics', () => {
    expect(mockFlowData.focus.title).toBeTruthy()
    expect(mockFlowData.focus.description).toBeTruthy()
    expect(mockFlowData.focus.metrics.length).toBeGreaterThan(0)
    for (const m of mockFlowData.focus.metrics) {
      expect(m.label).toBeTruthy()
      expect(m.value).toBeGreaterThanOrEqual(0)
    }
  })

  it('exposes a knowledge flow with 6 ordered steps', () => {
    expect(mockFlowData.flow.label).toBeTruthy()
    expect(mockFlowData.flow.steps).toHaveLength(6)
    const states = new Set(mockFlowData.flow.steps.map((s) => s.state))
    // 合法状态：done / current / todo
    expect([...states].every((s) => ['done', 'current', 'todo'].includes(s))).toBe(true)
    expect(mockFlowData.flow.steps.some((s) => s.state === 'current')).toBe(true)
  })

  it('exposes at least one pending candidate with tags and actions', () => {
    expect(mockFlowData.candidate.title).toBeTruthy()
    expect(mockFlowData.candidate.summary).toBeTruthy()
    expect(mockFlowData.candidate.tags.length).toBeGreaterThan(0)
    expect(mockFlowData.candidate.actions.length).toBeGreaterThan(0)
  })

  it('exposes knowledge domains with names and counts', () => {
    expect(mockFlowData.domains.length).toBeGreaterThan(0)
    for (const d of mockFlowData.domains) {
      expect(d.name).toBeTruthy()
      expect(d.count).toBeGreaterThanOrEqual(0)
    }
  })

  it('exposes a session memory card with text', () => {
    expect(mockFlowData.memory.title).toBeTruthy()
    expect(mockFlowData.memory.text).toBeTruthy()
    expect(mockFlowData.memory.hint).toBeTruthy()
  })
})
