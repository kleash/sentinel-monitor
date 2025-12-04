import { expect, test } from '@playwright/test';

test('item timeline shows events and expectations', async ({ page }) => {
  await page.goto('/item/TR123');
  await expect(page.getByText('TR123').first()).toBeVisible();
  await expect(page.locator('app-lifecycle-timeline')).toBeVisible();
  await expect(page.getByText('Pending Expectations')).toBeVisible();
  await expect(page.locator('.expectation')).toHaveCount(1);
  await expect(page.getByText('SYS3 ACK pending').first()).toBeVisible();
});
