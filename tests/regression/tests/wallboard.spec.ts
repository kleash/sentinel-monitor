import { expect, test } from '@playwright/test';
import { captureStep } from '../utils/artifacts';

test('wallboard shows live metrics, date filters, and correlation drill-down to lifecycle', async ({ page }) => {
  await page.goto('/wallboard');
  await expect(page.getByText('Trade Lifecycle')).toBeVisible();

  // Date filter: flip between Today, All days, Specific date
  await page.getByRole('button', { name: 'All days' }).click();
  await page.getByRole('button', { name: 'Specific date' }).click();
  const today = new Date().toISOString().slice(0, 10);
  const dateInput = page.locator('input[type="date"]');
  await dateInput.fill(today);
  await captureStep(page, 'wallboard-date-filter.png');

  const cards = page.locator('.workflow-card');
  expect(await cards.count()).toBeGreaterThan(0);
  const tradeCard = cards.filter({ hasText: 'Trade Lifecycle' }).first();
  await expect(tradeCard.locator('.group')).toHaveCount(2);
  await page.getByRole('button', { name: 'Refresh' }).click();
  await captureStep(page, 'wallboard-refreshed.png');

  // Correlation drill from workflow header
  await tradeCard.getByRole('button', { name: 'Correlation IDs' }).click();
  const drawer = page.locator('app-correlation-drawer');
  await expect(drawer).toBeVisible();
  await expect(drawer.getByText('Correlation ID')).toBeVisible();
  await expect(drawer.getByText('TR123')).toBeVisible({ timeout: 15000 });
  await drawer.getByLabel('Stage').selectOption({ label: /ingest/i });
  await captureStep(page, 'wallboard-correlation-drawer.png');

  // View lifecycle from drawer
  await drawer.getByRole('button', { name: 'View lifecycle' }).first().click();
  await expect(page).toHaveURL(/item\/TR/i);
  await expect(page.locator('app-lifecycle-timeline')).toBeVisible();
  await captureStep(page, 'wallboard-correlation-lifecycle.png');

  // Return and drill via group tile
  await page.goBack();
  const groupTile = tradeCard.locator('.group').first();
  await expect(groupTile.getByText('In Flight')).toBeVisible();
  await expect(groupTile.getByText('Late')).toBeVisible();
  await groupTile.click();

  await expect(page).toHaveURL(/workflow/);
  await expect(page.getByText('Trade Lifecycle')).toBeVisible();
  const stageTiles = page.locator('app-stage-tile');
  expect(await stageTiles.count()).toBeGreaterThan(2);
  await captureStep(page, 'wallboard-drill-into-workflow.png');
});
