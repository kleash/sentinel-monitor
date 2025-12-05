import { expect, test } from '@playwright/test';
import { captureStep } from '../utils/artifacts';

test('workflow lifecycle supports CRUD-style create/read drill with tiles', async ({ page }) => {
  const workflowKey = `wf-e2e-${Date.now()}`;
  const workflowName = `Playwright Flow ${workflowKey.slice(-4)}`;

  await page.goto('/rules');
  await page.getByLabel('Name').fill(workflowName);
  await page.getByLabel('Key').fill(workflowKey);
  await page.getByLabel('Created By').fill('playwright');
  await page.getByLabel('Runbook URL').fill('https://runbooks/playwright');
  await page.getByLabel('Group Dimensions (comma separated)').fill('book,region,desk');

  await page.getByRole('button', { name: 'Add Node' }).click();
  const newNode = page.locator('.section').filter({ hasText: 'Nodes' }).locator('.group-row').last();
  await newNode.locator('input[formcontrolname="key"]').fill('qa-check');
  await newNode.locator('input[formcontrolname="eventType"]').fill('QA_CHECKED');
  await newNode.getByRole('checkbox', { name: 'Terminal' }).check();

  await page.getByRole('button', { name: 'Add Edge' }).click();
  const newEdge = page.locator('.section').filter({ hasText: 'Edges' }).locator('.group-row').last();
  await newEdge.getByPlaceholder('ingest').fill('sys3-ack');
  await newEdge.getByPlaceholder('sys2-verify').fill('qa-check');
  await newEdge.locator('input[type="number"]').fill('600');
  await captureStep(page, `rules-form-${workflowKey}.png`);

  await page.getByRole('button', { name: 'Create' }).click();
  await expect(page.getByText(workflowKey)).toBeVisible();
  await captureStep(page, `rules-created-${workflowKey}.png`);

  // Read existing seeded workflow to demonstrate drill-in
  await page.goto('/workflow/trade-lifecycle?group=eqd-ny');
  await expect(page.getByText('Trade Lifecycle')).toBeVisible();
  await expect(page.locator('app-graph-canvas')).toBeVisible();
  const seededTiles = page.locator('app-stage-tile');
  expect(await seededTiles.count()).toBeGreaterThanOrEqual(3);
  await captureStep(page, 'workflow-seeded-trade.png');
});
