import {defineConfig} from '@playwright/test';

// Every spec runs twice: against the plain React reference and against JX.
const origin = 'http://127.0.0.1:' + (process.env.BENCH_PORT || 4573);
const browser = {browserName: 'chromium', channel: 'chrome', headless: true};
export default defineConfig({
  testDir: './specs', timeout: 20_000, fullyParallel: true,
  projects: [
    {name: 'reference', use: {...browser, baseURL: origin + '/reference/'}},
    {name: 'jx', use: {...browser, baseURL: origin + '/jx/'}}
  ],
  webServer: {command: 'node tests/conformance/serve.js', cwd: '../..', url: origin + '/reference/', reuseExistingServer: false},
  reporter: [['list'], ['./report.js']]
});
