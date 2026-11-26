import {test, expect} from '@playwright/test';

const browserErrors = new WeakMap();

const count = (page, kind, id, filter) => page.evaluate(({kind, id, filter}) =>
  (globalThis.routeResourceEvents || []).filter(event => event.kind === kind && event.id === id && event.filter === filter).length,
{kind, id, filter});

test.beforeEach(async ({page}) => {
  const errors = []; browserErrors.set(page, errors);
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await page.addInitScript(() => {globalThis.routeResourceFixture = true;});
  await page.goto('/todos/first?filter=all');
  await expect(page.locator('#route-resource-result')).toHaveText('loading');
  await expect.poll(() => count(page, 'start', 'first', 'all')).toBe(2);
});

test.afterEach(async ({page}) => {
  expect(browserErrors.get(page)).toEqual([]);
});

test('route loaders follow decoded parameters, queries and browser back/forward without accepting old results', async ({page}) => {
  await page.locator('#route-resource-pulse').click();
  await expect(page.locator('#route-resource-pulses')).toHaveText('1');
  expect(await count(page, 'start', 'first', 'all')).toBe(2);
  await page.locator('#route-resource-second').click();
  await expect(page).toHaveURL(/\/todos\/second%20item\?filter=active$/);
  await expect.poll(() => count(page, 'start', 'second item', 'active')).toBe(1);
  expect(await count(page, 'cancel', 'first', 'all')).toBe(2);
  await page.locator('#route-resource-finish-first').click();
  await expect(page.locator('#route-resource-result')).toHaveText('loading');
  await page.locator('#route-resource-query').click();
  await expect.poll(() => count(page, 'start', 'second item', 'done')).toBe(1);
  expect(await count(page, 'cancel', 'second item', 'active')).toBe(1);
  await page.locator('#route-resource-finish-active').click();
  await expect(page.locator('#route-resource-result')).toHaveText('loading');
  await page.locator('#route-resource-finish-done').click();
  await expect(page.locator('#route-resource-result')).toHaveText('second item|done');
  await page.goBack();
  await expect(page).toHaveURL(/\/todos\/first\?filter=all$/);
  await expect(page.locator('#route-resource-result')).toHaveText('loading');
  await expect.poll(() => count(page, 'start', 'first', 'all')).toBe(3);
  await page.locator('#route-resource-finish-done').click();
  await expect(page.locator('#route-resource-result')).toHaveText('loading');
  await page.locator('#route-resource-finish-first').click();
  await expect(page.locator('#route-resource-result')).toHaveText('first|all');
  await page.goForward();
  await expect(page).toHaveURL(/\/todos\/second%20item\?filter=done$/);
  await expect.poll(() => count(page, 'start', 'second item', 'done')).toBe(2);
  await page.locator('#route-resource-finish-done').click();
  await expect(page.locator('#route-resource-result')).toHaveText('second item|done');
});

test('unmount cancels the route loader while another router observer remains usable; remount and failure recover', async ({page}) => {
  await page.locator('#route-resource-mounted').click();
  await expect(page.locator('#route-resource-result')).toHaveCount(0);
  expect(await count(page, 'cancel', 'first', 'all')).toBe(2);
  await page.locator('#route-resource-second').click();
  await expect(page.locator('#route-resource-location')).toHaveText('/todos/second%20item?filter=active');
  expect(await count(page, 'start', 'second item', 'active')).toBe(0);
  await page.locator('#route-resource-finish-first').click();
  await page.locator('#route-resource-mounted').click();
  await expect.poll(() => count(page, 'start', 'second item', 'active')).toBe(2);
  await page.locator('#route-resource-finish-active').click();
  await expect(page.locator('#route-resource-result')).toHaveText('second item|active');
  await page.locator('#route-resource-denied').click();
  await expect.poll(() => count(page, 'start', 'denied', 'all')).toBe(1);
  await page.locator('#route-resource-finish-denied').click();
  await expect(page.locator('#route-resource-result')).toHaveText('failed:DENIED');
  const startsBeforeMissing = await page.evaluate(() => globalThis.routeResourceEvents.filter(event => event.kind === 'start').length);
  await page.locator('#route-resource-missing').click();
  await expect(page.locator('#route-resource-result')).toHaveText('failed:NOT_FOUND');
  expect(await page.evaluate(() => globalThis.routeResourceEvents.filter(event => event.kind === 'start').length)).toBe(startsBeforeMissing);
  await page.locator('#route-resource-second').click();
  await expect(page.locator('#route-resource-result')).toHaveText('loading');
  await page.locator('#route-resource-finish-active').click();
  await expect(page.locator('#route-resource-result')).toHaveText('second item|active');
});
