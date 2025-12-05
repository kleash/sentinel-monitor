import fs from 'fs';
import path from 'path';
import { Page } from '@playwright/test';

const ARTIFACT_ROOT = path.resolve(__dirname, '..', 'test-results', 'artifacts');

export function ensureArtifactDir() {
  fs.mkdirSync(ARTIFACT_ROOT, { recursive: true });
}

export function artifactPath(name: string) {
  return path.join(ARTIFACT_ROOT, name);
}

export async function captureStep(page: Page, name: string) {
  ensureArtifactDir();
  await page.screenshot({ path: artifactPath(name), fullPage: true });
}
