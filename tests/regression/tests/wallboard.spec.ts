import { expect, test } from '@playwright/test';

test('wallboard shows workflows and group metrics', async ({ page }) => {
  await page.goto('/wallboard');
  await expect(page.getByText('Trade Lifecycle')).toBeVisible();
  const cards = page.locator('.workflow-card');
  expect(await cards.count()).toBeGreaterThan(0);

  await expect(page.getByText('In Flight').first()).toBeVisible();
  await expect(page.getByText('Late').first()).toBeVisible();

  // Navigate via group selection
  await page.getByText('EQD / NY').click();
  await expect(page).toHaveURL(/workflow/);
});
