import {defineConfig} from '@playwright/test';

if (!process.env.ARTIFACT_CONSUMER_URL) throw new Error('Run through VerifyArtifactConsumer.java');
export default defineConfig({
  testDir: './browser-tests',
  timeout: 20_000,
  workers: 1,
  use: {baseURL: process.env.ARTIFACT_CONSUMER_URL, browserName: 'chromium', channel: 'chrome', headless: true},
  reporter: [['list']]
});
