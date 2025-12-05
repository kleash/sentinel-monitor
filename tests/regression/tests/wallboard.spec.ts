import { expect, test } from '@playwright/test';
import { captureStep } from '../utils/artifacts';

test('wallboard gives production support overview with drill-through', async ({ page }) => {
  await page.goto('/wallboard');
  await expect(page.getByText('Trade Lifecycle')).toBeVisible();
  const cards = page.locator('.workflow-card');
  expect(await cards.count()).toBeGreaterThan(0);
  await captureStep(page, 'wallboard-overview.png');

  const tradeCard = cards.filter({ hasText: 'Trade Lifecycle' }).first();
  await expect(tradeCard.locator('.group')).toHaveCount(2);
  await expect(tradeCard.locator('.countdowns app-countdown-badge').first()).toBeVisible();
  await page.getByRole('button', { name: 'Refresh' }).click();
  await captureStep(page, 'wallboard-refreshed.png');

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
