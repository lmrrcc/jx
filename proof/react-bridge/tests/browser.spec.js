import { test, expect } from '@playwright/test';

test('Java runs in React: independent snapshots, queued updates, offline clicks, cleanup', async ({ page, context }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') console.error(message.text()); });
  page.on('pageerror', error => console.error(error));
  await page.goto('/');
  await expect(page.locator('#First')).toHaveText('First: 0');
  await expect(page.locator('#Second')).toHaveText('Second: 10');
  await page.locator('#First').click();
  await expect(page.locator('#First')).toHaveText('First: 3');
  await expect(page.locator('#Second')).toHaveText('Second: 10');
  expect(await page.evaluate(() => globalThis.proofEvents.filter(e => e.kind === 'snapshot').at(-1).value)).toBe(0);
  await context.setOffline(true);
  await page.locator('#First').click();
  await expect(page.locator('#First')).toHaveText('First: 6');
  await page.locator('#toggle').click();
  await expect(page.locator('#First')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => globalThis.proofEvents.some(e => e.kind === 'cleanup' && e.label === 'First' && e.value === 6))).toBe(true);
  await page.locator('#Second').click();
  await expect(page.locator('#Second')).toHaveText('Second: 13');
  await page.locator('#toggle').click();
  await expect(page.locator('#First')).toHaveText('First: 0');
  await expect(page.locator('#Second')).toHaveText('Second: 13');
  expect(errors).toEqual([]);
});
