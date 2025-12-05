import { expect, test } from '@playwright/test';
import { captureStep } from '../utils/artifacts';

test('ingest simulator drives live wallboard + item updates', async ({ page }) => {
  const correlationKey = 'TR123';

  await page.goto('/wallboard');
  const wallboardCard = page.locator('.workflow-card', { hasText: 'Trade Lifecycle' });
  const initialGroup = wallboardCard.locator('.group').first();
  await captureStep(page, 'wallboard-before-ingest.png');

  await page.goto('/ingest');
  await page.getByLabel('Correlation Key').fill(correlationKey);
  await page.getByLabel('Event Type').fill('TRADE_INGEST');
  await page.getByLabel('Workflow Key').fill('trade-lifecycle');
  await page.getByLabel('Source System').fill('playwright');
  await page.getByLabel('Group (JSON)').fill('{"book":"EQD","region":"NY"}');
  await page.getByRole('button', { name: 'Send' }).click();
  await expect(page.locator('.result')).toHaveText(/Event sent/i);
  await captureStep(page, `ingest-${correlationKey}.png`);

  await page.goto('/wallboard');
  await page.getByRole('button', { name: 'Refresh' }).click();
  const refreshedGroup = page.locator('.workflow-card', { hasText: 'Trade Lifecycle' }).locator('.group').first();
  await expect(refreshedGroup).toBeVisible();
  await captureStep(page, `wallboard-after-ingest-${correlationKey}.png`);

  await page.goto(`/item/${correlationKey}`);
  await expect(page).toHaveURL(new RegExp(`/item/${correlationKey}`));
  await expect(page.getByText(correlationKey).first()).toBeVisible();
  await expect(page.locator('app-lifecycle-timeline')).toBeVisible();
  await captureStep(page, `item-timeline-${correlationKey}.png`);
});
