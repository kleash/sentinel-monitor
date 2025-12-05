import { expect, test } from '@playwright/test';
import { captureStep } from '../utils/artifacts';

test('rules page catalogs workflows and previews graph edits', async ({ page }) => {
  const key = `wf-rules-${Date.now()}`;
  await page.goto('/rules');
  await expect(page.getByText('Workflow Catalog')).toBeVisible();
  const catalog = page.locator('.wf');
  await expect(catalog.first()).toBeVisible({ timeout: 15000 });
  await captureStep(page, 'rules-catalog.png');

  await page.getByLabel('Name').fill(`Rules ${key}`);
  await page.getByLabel('Key').fill(key);
  await page.getByLabel('Created By').fill('playwright');
  await page.getByLabel('Runbook URL').fill('https://runbooks/rules-preview');

  await page.getByRole('button', { name: 'Add Node' }).click();
  const nodesSection = page.locator('.section').filter({ hasText: 'Nodes' });
  const newNode = nodesSection.locator('.group-row').last();
  await newNode.locator('input[formcontrolname="key"]').fill('ops-review');
  await newNode.locator('input[formcontrolname="eventType"]').fill('OPS_REVIEWED');

  await page.getByRole('button', { name: 'Add Edge' }).click();
  const edgesSection = page.locator('.section').filter({ hasText: 'Edges' });
  const newEdge = edgesSection.locator('.group-row').last();
  await newEdge.getByPlaceholder('ingest').fill('sys3-ack');
  await newEdge.getByPlaceholder('sys2-verify').fill('ops-review');

  await expect(page.locator('app-graph-canvas')).toBeVisible();
  await captureStep(page, `rules-preview-${key}.png`);

  await page.getByRole('button', { name: 'Create' }).click();
  await expect(async () => {
    await page.getByText(key);
  }).toPass({ timeout: 15000 });
  await captureStep(page, `rules-created-${key}.png`);
});
