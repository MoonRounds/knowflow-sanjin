import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import KfEmptyState from '../KfEmptyState.vue'

describe('KfEmptyState', () => {
  it('only uses the full-width layout when explicitly requested', () => {
    const compact = mount(KfEmptyState, {
      props: { title: '空状态', description: '说明' },
    })
    const wide = mount(KfEmptyState, {
      props: { title: '空状态', description: '说明', wide: true },
    })

    expect(compact.classes()).not.toContain('kf-empty--wide')
    expect(wide.classes()).toContain('kf-empty--wide')
  })
})
