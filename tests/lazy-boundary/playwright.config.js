import {defineConfig} from '@playwright/test';
export default defineConfig({
  testDir: './tests', timeout: 20_000,
  use: {baseURL: 'http://127.0.0.1:4577', browserName: 'chromium', channel: 'chrome', headless: true},
  webServer: {command: 'node tests/lazy-boundary/serve.js', cwd: '../..', url: 'http://127.0.0.1:4577', reuseExistingServer: false},
  reporter: [['list']]
});
