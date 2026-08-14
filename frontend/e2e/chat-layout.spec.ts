import { expect, test } from '@playwright/test'

test.describe('Chat 响应式布局', () => {
  test('窄屏时会话级操作保持右对齐', async ({ page }) => {
    await page.setViewportSize({ width: 620, height: 720 })
    await page.goto('/chat')

    const actions = page.locator('.chat-head-actions')
    const deleteButton = actions.getByRole('button', { name: '删除' })
    await expect(deleteButton).toBeVisible()

    const actionsBox = await actions.boundingBox()
    const deleteBox = await deleteButton.boundingBox()
    expect(actionsBox).not.toBeNull()
    expect(deleteBox).not.toBeNull()

    const rightGap = actionsBox!.x + actionsBox!.width - (deleteBox!.x + deleteBox!.width)
    expect(rightGap).toBeLessThanOrEqual(8)
  })
})
