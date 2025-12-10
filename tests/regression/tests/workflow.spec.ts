import { expect, test } from '@playwright/test';
import { captureStep } from '../utils/artifacts';

test('workflow lifecycle supports CRUD-style create/read drill with tiles', async ({ page }) => {
  const workflowKey = `wf-e2e-${Date.now()}`;
  const workflowName = `Playwright Flow ${workflowKey.slice(-4)}`;

  await page.goto('/rules');
  await page.getByLabel('Name').fill(workflowName);
  await page.getByLabel('Key').fill(workflowKey);
  await page.getByLabel('Created By').fill('playwright');
  await page.getByLabel('Group Dimensions (comma separated)').fill('book,region,desk');

  const nodeRows = page.locator('.section').filter({ hasText: 'Nodes' }).locator('.group-row');
  const ingestNode = nodeRows.nth(0);
  await ingestNode.locator('input[formcontrolname="key"]').fill('ingest');
  await ingestNode.locator('input[formcontrolname="eventType"]').fill('TRADE_INGEST');

  await page.getByRole('button', { name: 'Add Node' }).click();
  const sys2Node = nodeRows.nth(1);
  await sys2Node.locator('input[formcontrolname="key"]').fill('sys2-verify');
  await sys2Node.locator('input[formcontrolname="eventType"]').fill('SYS2_VERIFIED');

  await page.getByRole('button', { name: 'Add Node' }).click();
  const qaNode = nodeRows.nth(2);
  await qaNode.locator('input[formcontrolname="key"]').fill('qa-check');
  await qaNode.locator('input[formcontrolname="eventType"]').fill('QA_CHECKED');
  await qaNode.getByRole('checkbox', { name: 'Terminal' }).check();

  await page.getByRole('button', { name: 'Add Edge' }).click();
  const newEdge = page.locator('.section').filter({ hasText: 'Edges' }).locator('.group-row').last();
  const edgeRows = page.locator('.section').filter({ hasText: 'Edges' }).locator('.group-row');
  const firstEdge = edgeRows.nth(0);
  await firstEdge.getByPlaceholder('ingest').fill('ingest');
  await firstEdge.getByPlaceholder('sys2-verify').fill('sys2-verify');

  await newEdge.getByPlaceholder('ingest').fill('sys2-verify');
  await newEdge.getByPlaceholder('sys2-verify').fill('qa-check');
  await newEdge.getByPlaceholder('300').fill('600');
  await newEdge.getByPlaceholder('08:00Z').fill('09:00Z');
  await newEdge.getByPlaceholder('1').fill('3');
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
  await seededTiles.first().click();
  const drawer = page.locator('app-correlation-drawer');
  await expect(drawer).toBeVisible();
  await captureStep(page, 'workflow-correlation-drawer.png');
  await drawer.getByRole('button', { name: 'View lifecycle' }).first().click();
  await expect(page).toHaveURL(/item\//);
  await expect(page.locator('app-lifecycle-timeline')).toBeVisible();
  await page.goBack();

  // Drill from graph node to correlation list
  await page.locator('app-graph-canvas .node-group').first().click();
  await expect(drawer).toBeVisible();
  await drawer.getByRole('button', { name: 'Close' }).click();

  await captureStep(page, 'workflow-seeded-trade.png');
});
