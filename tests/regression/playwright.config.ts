import { defineConfig, devices } from '@playwright/test';
import path from 'path';

export default defineConfig({
  testDir: './tests',
  timeout: 90_000,
  fullyParallel: false,
  expect: {
    timeout: 12_000
  },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }]
  ],
  workers: 1,
  globalSetup: './global-setup.ts',
  globalTeardown: './global-teardown.ts',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:4300',
    trace: 'retain-on-failure',
    screenshot: 'on',
    video: { mode: 'on', size: { width: 1280, height: 720 } },
    headless: true,
    viewport: { width: 1400, height: 900 }
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ],
  coverage: {
    provider: 'v8'
  },
  webServer: {
    command: 'npm run start:mock -- --port 4300 --host 0.0.0.0',
    url: 'http://localhost:4300',
    reuseExistingServer: true,
    timeout: 120_000,
    cwd: path.resolve(__dirname, '../../frontend')
  }
});
