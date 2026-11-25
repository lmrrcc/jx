import {defineConfig} from '@playwright/test';
const origin = 'http://127.0.0.1:' + (process.env.RSC_PORT || 59182);
export default defineConfig({
  testDir: './tests', timeout: 20000, workers: 2,
  projects: ['reference', 'jx'].map(name => ({name, use: {baseURL: origin, browserName: 'chromium', channel: 'chrome', headless: true}})),
  webServer: {command: 'node --conditions=react-server tests/rsc-proof/serve.js', cwd: '../..', url: origin, reuseExistingServer: false},
  reporter: [['list']]
});
