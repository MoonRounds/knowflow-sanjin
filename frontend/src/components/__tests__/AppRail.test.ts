import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'

import AppRail from '../AppRail.vue'

async function mountRail(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/flow', component: { template: '<div />' } },
      { path: '/chat', component: { template: '<div />' } },
      { path: '/knowledge-bases', component: { template: '<div />' } },
      { path: '/candidates', component: { template: '<div />' } },
      { path: '/processing', component: { template: '<div />' } },
      { path: '/model-settings', component: { template: '<div />' } },
    ],
  })

  await router.push(path)
  await router.isReady()

  return mount(AppRail, { global: { plugins: [router] } })
}

describe('AppRail', () => {
  it('keeps scrollable work links separate from the fixed settings link', async () => {
    const wrapper = await mountRail('/processing')
    const workLinks = wrapper.findAll('.railnav .railbtn')
    const settingsLink = wrapper.get('.settingsbtn')

    expect(workLinks).toHaveLength(5)
    expect(workLinks.at(-1)?.attributes('href')).toBe('/processing')
    expect(workLinks.at(-1)?.classes()).toContain('active')
    expect(settingsLink.element.parentElement).toBe(wrapper.get('.rail').element)
    expect(settingsLink.classes()).not.toContain('active')
  })

  it('activates the fixed settings link independently', async () => {
    const wrapper = await mountRail('/model-settings')

    expect(wrapper.get('.settingsbtn').classes()).toContain('active')
    expect(wrapper.findAll('.railnav .railbtn.active')).toHaveLength(0)
  })
})
