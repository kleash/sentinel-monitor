import { expect, test } from '@playwright/test';

test('workflow view renders graph and stage tiles', async ({ page }) => {
  await page.goto('/workflow/trade-lifecycle?group=eqd-ny');
  await expect(page.getByText('Trade Lifecycle')).toBeVisible();
  await expect(page.locator('app-graph-canvas')).toBeVisible();
  const tiles = page.locator('app-stage-tile');
  expect(await tiles.count()).toBeGreaterThanOrEqual(3);
  await expect(page.getByText('At Risk')).toBeVisible();
});
