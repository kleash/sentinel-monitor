import { expect, test } from '@playwright/test';
import { captureStep } from '../utils/artifacts';

test('item timeline shows hop-by-hop trail, summaries, and alerts', async ({ page }) => {
  await page.goto('/item/TR123');
  await expect(page.getByText('TR123').first()).toBeVisible();
  await expect(page.locator('app-lifecycle-timeline')).toBeVisible();
  await expect(page.getByText(/Workflow:/)).toBeVisible();
  await expect(page.getByText(/Current Stage/)).toBeVisible();
  await expect(page.locator('.event-row').first()).toBeVisible();
  await expect(page.locator('.alerts .alert').first()).toBeVisible({ timeout: 5000 });
  await captureStep(page, 'item-tr123-timeline.png');
});
