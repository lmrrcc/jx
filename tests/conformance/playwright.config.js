import {defineConfig} from '@playwright/test';

// Every spec runs against the plain React reference and against JX, in each rendering mode.
const origin = 'http://127.0.0.1:' + (process.env.BENCH_PORT || 4573);
const browser = {browserName: 'chromium', channel: 'chrome', headless: true};
export default defineConfig({
  testDir: './specs', timeout: 20_000, fullyParallel: true,
  projects: [
    {name: 'reference', use: {...browser, baseURL: origin + '/reference/'}},
    {name: 'jx', use: {...browser, baseURL: origin + '/jx/'}},
    // Hydrate mode: each client case is rendered in Node first and hydrated in the browser.
    {name: 'reference-hydrate', use: {...browser, baseURL: origin + '/reference/'}},
    {name: 'jx-hydrate', use: {...browser, baseURL: origin + '/jx/'}},
    // Static mode: each client case as static HTML from Node; JX must produce React's exact markup.
    {name: 'reference-static', use: {...browser, baseURL: origin + '/reference/'}},
    {name: 'jx-static', use: {...browser, baseURL: origin + '/jx/'}}
  ],
  webServer: {command: 'node tests/conformance/serve.js', cwd: '../..', url: origin + '/reference/', reuseExistingServer: false},
  reporter: [['list'], ['./report.js']]
});
