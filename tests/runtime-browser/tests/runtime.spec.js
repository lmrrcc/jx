import {test, expect} from '@playwright/test';

test('real React owns mounted state, snapshots, keys and Strict Mode cleanup, even offline', async ({page, context}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.locator('#counter-A')).toHaveText('A: 0 dark');
  await context.setOffline(true);
  await page.locator('#counter-A').click();
  await expect(page.locator('#counter-A')).toHaveText('A: 3 dark');
  await expect(page.locator('#counter-B')).toHaveText('B: 10 dark');
  await page.locator('#parent').click();
  await expect(page.locator('#context-stable')).toHaveText('stable');
  await page.locator('#reverse').click();
  await expect(page.locator('#counters button').first()).toHaveText('B: 10 dark');
  await expect(page.locator('#counter-A')).toHaveText('A: 3 dark');
  await page.locator('#hide').click();
  await expect(page.locator('#counter-A')).toHaveCount(0);
  await page.locator('#hide').click();
  await expect(page.locator('#counter-A')).toHaveText('A: 0 dark');
  const events = await page.evaluate(() => globalThis.runtimeEvents);
  expect(events).toContainEqual({kind: 'snapshot', label: 'A', value: 0});
  expect(events).toContainEqual({kind: 'cleanup', label: 'A', value: 3});
  expect(events.filter(e => e.kind === 'setup' && e.label === 'A' && e.value === 0).length).toBeGreaterThanOrEqual(2);
  expect(errors).toEqual([]);
});

test('scalar dependencies, opaque identity, callable values, reducers, refs, controlled forms and escaping', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  const input = page.getByLabel('Controlled value');
  await expect(input).toBeFocused();
  const count = await page.evaluate(() => globalThis.runtimeEvents.filter(e => e.kind === 'scalar-effect').length);
  const identityCount = await page.evaluate(() => globalThis.runtimeEvents.filter(e => e.kind === 'identity-effect').length);
  await page.locator('#noise').click();
  await expect(page.locator('#noise')).toHaveText('Noise 1');
  await page.locator('#scalar').click();
  expect(await page.evaluate(() => globalThis.runtimeEvents.filter(e => e.kind === 'scalar-effect').length)).toBe(count);
  expect(await page.evaluate(() => globalThis.runtimeEvents.filter(e => e.kind === 'identity-effect').length)).toBeGreaterThan(identityCount);
  await page.locator('#callable').click();
  await expect(input).toHaveValue('second');
  await input.fill('edited');
  await expect(input).toHaveValue('edited');
  await page.locator('#reduce').click();
  await expect(page.locator('#reduce')).toHaveText('Reducer 3');
  await expect(page.locator('#callback-stable')).toHaveText('stable');
  await expect(page.locator('#escaped')).toHaveText('<script>unsafe</script>');
  await expect(page.locator('#escaped script')).toHaveCount(0);
  await expect(page.locator('#escaped')).toHaveCSS('color', 'rgb(1, 2, 3)');
  expect(errors).toEqual([]);
});

test('MVU commits, stale replies and subscription disposal', async ({page}) => {
  await page.goto('/');
  await page.locator('#elm-increment').click();
  await expect(page.locator('#model')).toHaveText('1 idle');
  await page.locator('#slow').click();
  await expect(page.locator('#model')).toHaveText('1 loading');
  await page.locator('#fast').click();
  await expect(page.locator('#model')).toHaveText('2 success');
  await expect.poll(() => page.evaluate(() => globalThis.runtimeEvents.some(e => e.kind === 'command-cancel' && e.value === 1))).toBe(true);
  await page.locator('#subscription').click();
  await page.locator('#subscription').click();
  expect(await page.evaluate(() => globalThis.runtimeEvents.filter(e => e.kind.startsWith('subscription-')).map(e => e.kind))).toEqual(['subscription-start', 'subscription-stop']);
  // Observe the deliberately uncancellable old response after its bounded timer has completed.
  await expect.poll(() => page.evaluate(() => globalThis.runtimeEvents.some(e => e.kind === 'response-attempt' && e.value === 1))).toBe(true);
  await expect(page.locator('#model')).toHaveText('2 success');
});

test('Java render exceptions, explicit event error routing and both React interop directions', async ({page}) => {
  await page.goto('/');
  await page.locator('#foreign').click();
  await expect(page.locator('#foreign')).toHaveText('Java bound prop: 1');
  await page.locator('#counter-Export').click();
  await expect(page.locator('#counter-Export')).toHaveText('Export: 23 default');
  await page.locator('#throw-event').click();
  await expect(page.locator('#event-error')).toHaveText('event exploded');
  await page.locator('#throw-render').click();
  await expect(page.locator('#render-error')).toHaveText('render exploded');
  await page.locator('#counter-B').click();
  await expect(page.locator('#counter-B')).toHaveText('B: 13 dark');
});

test('typed HTTP, bounded reads, malformed/schema errors, timeout and cancellation', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  for (const [button, expected] of [
    ['http-get', '200 ready'], ['http-post', '201 posted'], ['http-error', 'HTTP 409'],
    ['http-invalid', 'INVALID_JSON 200'], ['http-decode', 'DECODE 200'], ['http-empty', '204 empty'],
    ['http-limit', 'PAYLOAD_LIMIT 0'], ['http-timeout', 'TIMEOUT 0']
  ]) {await page.locator(`#${button}`).click(); await expect(page.locator('#http-result')).toHaveText(expected);}
  await page.locator('#http-cancel').click();
  await expect(page.locator('#http-result')).toHaveText('cancelled');
  await page.locator('#http-get').click();
  await expect(page.locator('#http-result')).toHaveText('200 ready');
  expect(errors).toEqual([]);
});

test('history push/replace, URL parameters and browser back/forward', async ({page}) => {
  await page.goto('/');
  await expect(page.locator('#location')).toHaveText('/ all none');
  await page.locator('#navigate').click();
  await expect(page).toHaveURL(/\/todos\/item%20one\?filter=active$/);
  await expect(page.locator('#location')).toHaveText('/todos/item%20one active item one');
  await page.locator('#replace').click();
  await expect(page.locator('#location')).toHaveText('/todos/item%20two done item two');
  await page.goBack();
  await expect(page.locator('#location')).toHaveText('/ all none');
  await page.goForward();
  await expect(page.locator('#location')).toHaveText('/todos/item%20two done item two');
});
