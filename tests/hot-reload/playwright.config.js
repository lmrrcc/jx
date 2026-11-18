import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.', testMatch: 'hot.spec.js', workers: 1, timeout: 240_000,
  expect: { timeout: 120_000 },
  use: { browserName: 'chromium', channel: 'chrome', headless: true },
  reporter: [['list']]
});
