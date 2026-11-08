import { defineConfig } from '@playwright/test'

if (process.env.PAPERHUB_E2E_ISOLATED !== 'true')
  throw new Error(
    'Use paperhub-back/scripts/verify_mysql.py --frontend-e2e; browser mutations require the disposable test database',
  )
const baseURL = process.env.PAPERHUB_E2E_BASE_URL
if (!baseURL || !process.env.PAPERHUB_E2E_PDF)
  throw new Error('Missing isolated browser environment')
const port = Number(new URL(baseURL).port)
export default defineConfig({
  testDir: './tests/e2e',
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 12_000 },
  reporter: [
    ['list'],
    ['json', { outputFile: '../paperhub-back/target/frontend-e2e-report.json' }],
  ],
  use: {
    baseURL,
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    channel: 'chrome',
    headless: true,
    viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${port}`,
    url: baseURL,
    reuseExistingServer: false,
    timeout: 45_000,
  },
})
