import {test, expect} from '@playwright/test';

const events = page => page.evaluate(() => globalThis.resourceEvents || []);
const count = async (page, kind, value) => (await events(page)).filter(event => event.kind === kind && event.value === value).length;

test('resource keys own independent requests and reject late or duplicate completion under Strict Mode', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/?fixture=resource');
  await expect(page.locator('#resource-left')).toHaveText('loading');
  await expect.poll(() => count(page, 'start', 'A')).toBe(2);
  expect(await count(page, 'cancel', 'A')).toBe(1);
  await page.locator('#resource-pulse').click();
  await expect(page.locator('#resource-pulses')).toHaveText('1');
  expect(await count(page, 'start', 'A')).toBe(2);
  await page.locator('#resource-finish-other').click();
  await expect(page.locator('#resource-right')).toHaveText('ready:other');
  await page.locator('#resource-key-b').click();
  await expect.poll(() => count(page, 'start', 'B')).toBe(1);
  expect(await count(page, 'cancel', 'A')).toBe(2);
  await page.locator('#resource-finish-a').click();
  await expect(page.locator('#resource-left')).toHaveText('loading');
  await page.locator('#resource-finish-b').click();
  await expect(page.locator('#resource-left')).toHaveText('ready:B');
  await page.locator('#resource-fail-b').click();
  await expect(page.locator('#resource-left')).toHaveText('ready:B');
  expect(await count(page, 'cancel', 'B')).toBe(1);
  await page.locator('#resource-key-c').click();
  await expect(page.locator('#resource-left')).toHaveText('loading');
  await page.locator('#resource-mounted').click();
  await expect(page.locator('#resource-left')).toHaveCount(0);
  expect(await count(page, 'cancel', 'C')).toBe(1);
  await page.locator('#resource-finish-c').click();
  await expect(page.locator('#resource-right')).toHaveText('ready:other');
  expect((await events(page)).filter(event => event.kind === 'snapshot' && event.value.startsWith('B:'))
    .some(event => event.value.includes('ready:A'))).toBe(false);
  expect(errors).toEqual([]);
});

test('resource effects cancel while Activity is hidden and restart with typed failure on reconnection', async ({page}) => {
  await page.goto('/?fixture=resource');
  await page.evaluate(async () => {
    const {resourceLifecycleProbe} = await import('/application.js');
    globalThis.resourceNative = resourceLifecycleProbe();
    globalThis.resourceNative.render('A', true);
  });
  const nativeCount = (kind, value = 'B') => page.evaluate(({kind, value}) =>
    globalThis.resourceNative.events.filter(event => event.kind === kind && event.value === value).length, {kind, value});
  await expect.poll(() => count(page, 'start', 'A')).toBe(2);
  await expect.poll(() => nativeCount('start', 'A')).toBe(2);
  await page.evaluate(() => globalThis.resourceNative.render('B', true));
  await page.locator('#resource-key-b').click();
  await expect.poll(() => count(page, 'start', 'B')).toBe(1);
  await expect.poll(() => nativeCount('start')).toBe(1);
  await page.evaluate(() => globalThis.resourceNative.render('B', false));
  await page.locator('#resource-visible').click();
  await expect(page.locator('#resource-left')).toBeHidden();
  await expect.poll(() => count(page, 'cancel', 'B')).toBe(1);
  await expect.poll(() => nativeCount('cancel')).toBe(1);
  await page.locator('#resource-finish-b').click();
  await page.locator('#resource-visible').click();
  await page.evaluate(() => globalThis.resourceNative.render('B', true));
  await expect(page.locator('#resource-left')).toBeVisible();
  await expect(page.locator('#resource-left')).toHaveText('loading');
  // Strict Mode replays setup/cleanup again when this Activity reconnects.
  // Establish that with the independent native effect, then compare ownership.
  await expect.poll(() => nativeCount('start')).toBe(3);
  await expect.poll(() => count(page, 'start', 'B')).toBe(await nativeCount('start'));
  expect(await count(page, 'cancel', 'B')).toBe(await nativeCount('cancel'));
  await page.locator('#resource-fail-b').click();
  await expect(page.locator('#resource-left')).toHaveText('failed:denied:B');
  expect(await count(page, 'cancel', 'B')).toBe(await count(page, 'start', 'B'));
  await page.evaluate(() => globalThis.resourceNative.close());
});

test('HTTP resource preserves structured failures and aborts the real request on key change and unmount', async ({page}) => {
  await page.goto('/?fixture=resource');
  await expect(page.locator('#resource-http')).toHaveText('ready:ready');
  await page.locator('#resource-http-error').click();
  await expect(page.locator('#resource-http')).toHaveText('failed:HTTP:409');
  const metrics = async () => (await page.request.get('/api/resource-state')).json();
  const before = (await metrics()).closed;
  await page.locator('#resource-http-held').click();
  await expect(page.locator('#resource-http')).toHaveText('loading');
  await expect.poll(async () => (await metrics()).active).toBe(1);
  await page.locator('#resource-http-value').click();
  await expect(page.locator('#resource-http')).toHaveText('ready:ready');
  await expect.poll(async () => (await metrics()).closed).toBe(before + 1);
  expect((await metrics()).active).toBe(0);
  await page.locator('#resource-http-held').click();
  await expect.poll(async () => (await metrics()).active).toBe(1);
  await page.locator('#resource-http-mounted').click();
  await expect(page.locator('#resource-http')).toHaveCount(0);
  await expect.poll(async () => (await metrics()).closed).toBe(before + 2);
  expect((await metrics()).active).toBe(0);
});

test('an abandoned suspended render never starts resource work', async ({page}) => {
  await page.goto('/?fixture=resource');
  await page.locator('#resource-uncommitted').click();
  await expect(page.locator('#resource-suspended')).toHaveText('waiting');
  expect(await count(page, 'start', 'uncommitted')).toBe(0);
  await page.locator('#resource-uncommitted').click();
  await expect(page.locator('#resource-suspended')).toHaveCount(0);
  await page.locator('#resource-open-gate').click();
  await page.locator('#resource-pulse').click();
  await expect(page.locator('#resource-pulses')).toHaveText('1');
  expect(await count(page, 'start', 'uncommitted')).toBe(0);
  // Positive control: the same child starts its task after a real commit.
  await page.locator('#resource-uncommitted').click();
  await expect.poll(() => count(page, 'start', 'uncommitted')).toBeGreaterThan(0);
});

test('synchronous task completion releases every Strict Mode owner without restarting on a parent pulse', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/?fixture=resource');
  await expect(page.locator('#resource-synchronous')).toHaveText('ready:immediate');
  expect(await count(page, 'start', 'synchronous')).toBe(2);
  expect(await count(page, 'cancel', 'synchronous')).toBe(2);
  await page.locator('#resource-pulse').click();
  await expect(page.locator('#resource-pulses')).toHaveText('1');
  await expect(page.locator('#resource-synchronous')).toHaveText('ready:immediate');
  expect(await count(page, 'start', 'synchronous')).toBe(2);
  expect(errors).toEqual([]);
});

test('unexpected loader and start exceptions reach a real React boundary and cannot publish later', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/?fixture=resource');
  await page.locator('#resource-throw-loader').click();
  await expect(page.locator('#resource-unexpected-error')).toHaveText('resource loader failed');
  await page.locator('#resource-throw-start').click();
  await expect(page.locator('#resource-unexpected-error')).toHaveText('resource start failed');
  await page.locator('#resource-finish-failed').click();
  await expect(page.locator('#resource-unexpected-error')).toHaveText('resource start failed');
  await expect(page.locator('#resource-unexpected-success')).toHaveCount(0);
  await page.locator('#resource-finish-other').click();
  await expect(page.locator('#resource-right')).toHaveText('ready:other');
  expect(errors).toEqual([]);
});
