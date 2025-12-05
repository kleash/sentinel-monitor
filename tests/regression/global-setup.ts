import { FullConfig } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { ensureArtifactDir } from './utils/artifacts';

function ensureDeps(dir: string) {
  const nodeModules = path.join(dir, 'node_modules');
  if (fs.existsSync(nodeModules)) {
    return;
  }
  execSync('npm install', { cwd: dir, stdio: 'inherit' });
}

export default async function globalSetup(_config: FullConfig) {
  const repoRoot = path.resolve(__dirname, '..', '..');
  const regressionRoot = __dirname;

  ['test-results', 'playwright-report'].forEach((dir) =>
    fs.rmSync(path.join(regressionRoot, dir), { recursive: true, force: true })
  );
  ensureArtifactDir();

  ensureDeps(path.join(repoRoot, 'frontend'));
  ensureDeps(regressionRoot);

  execSync('./scripts/start.sh', {
    cwd: repoRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      SKIP_FRONTEND: 'true',
      FRONTEND_PORT: process.env.FRONTEND_PORT ?? '4300',
      BACKEND_PORT: process.env.BACKEND_PORT ?? '8080'
    }
  });

  execSync('./scripts/seed.sh', {
    cwd: repoRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      API_URL: `http://localhost:${process.env.BACKEND_PORT ?? '8080'}`
    }
  });
}
