import { defineConfig, devices } from '@playwright/test';

/**
 * Runs against a stack that is already up (docker compose up in modernization/), because the sign-on
 * these tests exercise is the realm's: the browser is redirected to Keycloak, which asks for the
 * password and the one-time code, and only then returns to the screens.
 *
 * <p>Serial workers, one browser: the realm counts failed sign-on attempts per user, and two tests
 * signing in as the same user at the same time would make a brute-force test unreadable. A test is
 * allowed a minute and a half because a sign-on may have to wait out a 30 second TOTP step before the
 * realm will accept a code that has not been used yet.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
});
