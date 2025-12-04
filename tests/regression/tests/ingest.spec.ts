import { expect, test } from '@playwright/test';

test('ingest simulator posts event', async ({ page }) => {
  await page.goto('/ingest');
  await page.getByLabel('Correlation Key').fill('PX-INGEST');
  await page.getByLabel('Event Type').fill('TRADE_INGEST');
  await page.getByRole('button', { name: 'Send' }).click();
  await expect(page.locator('.result')).toHaveText(/Event sent/i);
});
