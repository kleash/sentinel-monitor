import { expect, test } from '@playwright/test';

test('workflow form creates new definition', async ({ page }) => {
  const key = `wf-${Date.now()}`;
  await page.goto('/rules');
  await page.getByLabel('Name').fill('Playwright Flow');
  await page.getByLabel('Key').fill(key);
  await page.getByLabel('Created By').fill('playwright');
  await page.getByLabel('Runbook URL').fill('https://runbooks/example');
  await page.getByRole('button', { name: 'Create' }).click();
  await expect(page.getByText(key)).toBeVisible();
});
