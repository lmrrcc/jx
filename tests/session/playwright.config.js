import { defineConfig } from '@playwright/test';
import { fileURLToPath } from 'node:url';

const repository = fileURLToPath(new URL('../../.cache/maven-repository/', import.meta.url));
const quotedRepository = "'" + repository.replaceAll("'", "'\\''") + "'";

export default defineConfig({
  testDir: '.', testMatch: '*.spec.js', workers: 1, timeout: 40_000, reporter: 'list',
  use: { browserName: 'chromium', channel: 'chrome', headless: true },
  projects: [
    { name: 'packaged', use: { baseURL: 'http://127.0.0.1:4582' } },
    { name: 'development', use: { baseURL: 'http://127.0.0.1:4883' } },
  ],
  webServer: [
    { command: 'java tools/PackagedTodo.java 4582', cwd: '../..', url: 'http://127.0.0.1:4582/api/session',
      reuseExistingServer: false, timeout: 30_000 },
    { command: `./mvnw -B -ntp -f examples/todo-app/pom.xml -Dmaven.repo.local=${quotedRepository} -Djx.port=4883 -Djx.apiOrigin=http://127.0.0.1:4582 -Djx.apiSessionCookie=JXSESSION dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev`,
      cwd: '../..', url: 'http://127.0.0.1:4883/todos', reuseExistingServer: false, timeout: 120_000 },
  ],
});
