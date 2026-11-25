import {defineConfig} from '@playwright/test';
const origin = 'http://127.0.0.1:' + (process.env.RUNTIME_PORT || 4373);
export default defineConfig({
  testDir: './tests', timeout: 20_000,
  use: {baseURL: origin, browserName: 'chromium', channel: 'chrome', headless: true},
  webServer: {command: 'node tests/runtime-browser/serve.js', cwd: '../..', url: origin, reuseExistingServer: false},
  reporter: [['list']]
});
