import { expect, test } from '@playwright/test';
import { captureStep } from '../utils/artifacts';

test('alerts console supports full lifecycle with evidence', async ({ page }) => {
  await page.goto('/alerts');
  const alert = page.locator('.alert').first();
  await expect(alert).toBeVisible();
  await expect(alert).toContainText(/open|pending/i);
  await captureStep(page, 'alerts-console-initial.png');

  await alert.getByRole('button', { name: 'Ack' }).click();
  await page.getByRole('button', { name: 'Refresh' }).click();
  await expect(alert).toContainText(/ack|open/i);
  await captureStep(page, 'alerts-console-ack.png');

  await alert.getByRole('button', { name: 'Suppress' }).click();
  await page.getByRole('button', { name: 'Refresh' }).click();
  await expect(alert).toContainText(/suppress|ack|open/i);
  await captureStep(page, 'alerts-console-suppress.png');

  await alert.getByRole('button', { name: 'Resolve' }).click();
  await page.getByRole('button', { name: 'Refresh' }).click();
  await expect(alert).toContainText(/resolved/i);
  await captureStep(page, 'alerts-console-resolved.png');
});
