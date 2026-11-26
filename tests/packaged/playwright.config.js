import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.', testMatch: '*.spec.js', workers: 1, timeout: 30_000, reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:4582', browserName: 'chromium', channel: 'chrome', headless: true },
  webServer: {
    command: 'java tools/PackagedTodo.java 4582', cwd: '../..',
    url: 'http://127.0.0.1:4582/api/todos', reuseExistingServer: false, timeout: 30_000,
  },
});
