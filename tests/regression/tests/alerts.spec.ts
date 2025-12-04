import { expect, test } from '@playwright/test';

test('alerts console supports lifecycle actions', async ({ page }) => {
  await page.goto('/alerts');
  const firstAlert = page.locator('main .alert').first();
  await expect(firstAlert).toBeVisible();
  await firstAlert.getByRole('button', { name: 'Ack' }).click();
  await expect(firstAlert.locator('.state')).toHaveText(/ack/i);

  await firstAlert.getByRole('button', { name: 'Resolve' }).click();
  await expect(firstAlert.locator('.state')).toHaveText(/resolved/i);
});
