import {expect, test} from '@playwright/test';

const reportedErrors = new WeakMap();
test.beforeEach(async ({page}) => {
  const errors = [];
  reportedErrors.set(page, errors);
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await page.goto('/?fixture=scalar-boundary');
  await expect(page.locator('#state-value')).toHaveText('+0');
  await expect.poll(() => page.evaluate(() => globalThis.scalarEvents?.some(event => event.kind === 'effect'))).toBe(true);
  await page.evaluate(() => { globalThis.scalarEvents = []; });
});
test.afterEach(async ({page}) => { expect(reportedErrors.get(page)).toEqual([]); });

test('numeric state distinguishes both signed zeros and bails out on the same sign', async ({page}) => {
  for (const [button, sign] of [['state-negative', '-0'], ['state-positive', '+0']]) {
    await page.locator(`#${button}`).click();
    await expect(page.locator('#state-value')).toHaveText(sign);
    await expect.poll(() => page.evaluate(() => globalThis.scalarEvents.filter(event => event.kind === 'state')))
      .toEqual([{kind: 'state', value: sign}]);
    expect(await page.evaluate(() => globalThis.scalarEvents.filter(event => event.kind === 'state-commit')))
      .toEqual([{kind: 'state-commit', value: sign}]);
    await page.evaluate(() => { globalThis.scalarEvents = []; });
    await page.locator(`#${button}`).click();
    // A browser frame lets an incorrect scheduled commit become observable.
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    expect(await page.evaluate(() => globalThis.scalarEvents.filter(event => event.kind.startsWith('state')))).toEqual([]);
  }
});

test('all dependency consumers preserve signed zero through forced independent renders', async ({page}) => {
  for (const [button, sign] of [['dependency-negative', '-0'], ['dependency-positive', '+0']]) {
    await page.locator(`#${button}`).click();
    await expect(page.locator('#dependency-value')).toHaveText(sign);
    await expect(page.locator('#memo-value')).toHaveText(sign);
    await expect(page.locator('#callback-value')).toHaveText(sign);
    await expect.poll(() => page.evaluate(() => globalThis.scalarEvents.filter(event => ['effect', 'layout', 'insertion'].includes(event.kind))))
      .toEqual([{kind: 'insertion', value: sign}, {kind: 'layout', value: sign}, {kind: 'effect', value: sign}]);
    await page.locator('#inspect-handle').click();
    await expect(page.locator('#handle-value')).toHaveText(sign);
    await page.evaluate(() => { globalThis.scalarEvents = []; });
    const before = Number(await page.locator('#noise-value').textContent());
    await page.locator('#dependency-noise').click();
    await expect(page.locator('#noise-value')).toHaveText(String(before + 1));
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    expect(await page.evaluate(() => globalThis.scalarEvents)).toEqual([]);
  }
});

test('context invalidates a memoized consumer for either signed-zero transition', async ({page}) => {
  for (const [button, sign] of [['context-negative', '-0'], ['context-positive', '+0']]) {
    await page.locator(`#${button}`).click();
    await expect(page.locator('#provider-value')).toHaveText(sign);
    await expect(page.locator('#context-value')).toHaveText(sign);
  }
});
