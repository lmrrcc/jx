import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: 'tests', timeout: 30_000, workers: 1, reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:4581', browserName: 'chromium', channel: 'chrome', headless: true },
  webServer: {
    command: 'java -jar examples/todo-api/target/todo-api-0.1.0-SNAPSHOT.jar --server.port=4581 --spring.datasource.url=jdbc:h2:mem:jx-browser-test --spring.main.banner-mode=off --logging.level.root=WARN',
    cwd: '../..', url: 'http://127.0.0.1:4581/api/todos', reuseExistingServer: false, timeout: 30_000,
  },
});
