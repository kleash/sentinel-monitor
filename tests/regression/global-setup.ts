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
  const backendPort = process.env.BACKEND_PORT ?? '8080';
  const frontendPort = process.env.FRONTEND_PORT ?? '4300';

  ['test-results', 'playwright-report'].forEach((dir) =>
    fs.rmSync(path.join(regressionRoot, dir), { recursive: true, force: true })
  );
  ensureArtifactDir();

  ensureDeps(path.join(repoRoot, 'frontend'));
  ensureDeps(regressionRoot);

  const backendJar = path.join(repoRoot, 'backend', 'platform-service', 'target', 'platform-service-0.0.1-SNAPSHOT.jar');
  if (!fs.existsSync(backendJar)) {
    const mvnCmd = fs.existsSync(path.join(repoRoot, 'backend', 'platform-service', 'mvnw')) ? './mvnw' : 'mvn';
    execSync(`${mvnCmd} -q -DskipTests package`, {
      cwd: path.join(repoRoot, 'backend', 'platform-service'),
      stdio: 'inherit'
    });
  }

  execSync('./scripts/start.sh', {
    cwd: repoRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      SKIP_FRONTEND: 'false',
      FRONTEND_PORT: frontendPort,
      BACKEND_PORT: backendPort,
      FRONTEND_START_CMD: `npm run start:demo -- --host 0.0.0.0 --port ${frontendPort} --proxy-config proxy.conf.js`
    }
  });

  execSync('./scripts/seed.sh', {
    cwd: repoRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      API_URL: `http://localhost:${backendPort}`
    }
  });
}
