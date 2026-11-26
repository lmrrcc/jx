import {defineConfig} from '@playwright/test';
import {randomBytes} from 'node:crypto';

// A fresh process-only capability protects scheduling/observation controls. It
// never enters the response, Java code, browser bundle or stored report.
process.env.PROTECTED_PROOF_TOKEN ||= randomBytes(24).toString('hex');

export default defineConfig({
  testDir: '.', testMatch: '*.spec.js', workers: 1, timeout: 60_000, reporter: 'list',
  use: {baseURL: 'http://127.0.0.1:4884', browserName: 'chromium', channel: 'chrome', headless: true},
  webServer: [
    {command: 'java tools/PackagedTodo.java 4885', cwd: '../..',
      url: 'http://127.0.0.1:4885/api/session', reuseExistingServer: false, timeout: 30_000},
    {command: 'node tests/protected-ssr/host.js', cwd: '../..',
      url: 'http://127.0.0.1:4884/ready', reuseExistingServer: false, timeout: 30_000},
  ],
});
