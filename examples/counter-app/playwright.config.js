import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './browser-tests',
  timeout: 30_000,
  use: { baseURL: 'http://127.0.0.1:4473', browserName: 'chromium', channel: 'chrome', headless: true },
  webServer: {
    command: './mvnw -B -ntp -Dmaven.repo.local=.cache/maven-repository -f examples/counter-app/pom.xml dev.jx:jx-maven-plugin:0.1.0-SNAPSHOT:dev -Djx.port=4473',
    cwd: '../..',
    url: 'http://127.0.0.1:4473',
    timeout: 180_000,
    reuseExistingServer: false
  },
  reporter: [['list']]
});
