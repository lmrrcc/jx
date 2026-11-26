import { defineConfig } from '@playwright/test';

const port = Number(process.env.TODO_PORT ?? 4581);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid TODO_PORT');
const origin = `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: 'tests', timeout: 30_000, workers: 1, reporter: 'list',
  use: { baseURL: origin, browserName: 'chromium', channel: 'chrome', headless: true },
  webServer: {
    command: `java -jar examples/todo-api/target/todo-api-0.1.0-SNAPSHOT.jar --server.port=${port} --spring.profiles.active=contract-proof --spring.datasource.url=jdbc:h2:mem:jx-browser-test --spring.main.banner-mode=off --logging.level.root=WARN`,
    cwd: '../..', url: `${origin}/api/todos`, reuseExistingServer: false, timeout: 30_000,
  },
});
