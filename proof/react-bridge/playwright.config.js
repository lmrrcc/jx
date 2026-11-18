import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests',
  timeout: 20_000,
  use: { baseURL: 'http://127.0.0.1:4273', browserName: 'chromium', channel: 'chrome', headless: true },
  webServer: { command: 'node proof/react-bridge/serve.js', cwd: '../..', env: { JX_PROOF_PORT: '4273' }, url: 'http://127.0.0.1:4273', reuseExistingServer: false },
  reporter: [['list']]
});
