import { defineConfig, devices } from '@playwright/test';
import path from 'path';

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: {
    timeout: 10_000
  },
  fullyParallel: true,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: 'http://localhost:4300',
    trace: 'on-first-retry',
    headless: true
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
    command: 'npm run start:mock -- --port 4300',
    url: 'http://localhost:4300',
    reuseExistingServer: true,
    timeout: 120_000,
    cwd: path.resolve(__dirname, '../../frontend')
  }
});
