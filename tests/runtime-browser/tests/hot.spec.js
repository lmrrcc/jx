import {test, expect} from '@playwright/test';

async function replace(page, changes = {}) {
  return page.evaluate(async changes => {
    Object.assign(globalThis, changes);
    const next = await import('/application.js?generation=' + crypto.randomUUID());
    const independent = next.main !== globalThis.hotFixtureModule.main;
    const snapshot = globalThis.__JX_HOT__.capture();
    const serialized = JSON.stringify(snapshot);
    globalThis.__JX_HOT__.dispose();
    globalThis.__JX_HOT__.restore(JSON.parse(serialized));
    next.main([]);
    globalThis.hotFixtureModule = next;
    await globalThis.__JX_HOT__.ready();
    return {independent, snapshot: JSON.parse(serialized)};
  }, changes);
}

test('independent TeaVM generations preserve committed scalar, keyed, reducer, record and Elm state', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/hot');
  await expect(page.locator('#hot-label')).toHaveText('Before edit');
  await page.locator('#hot-counter-A').click();
  await page.locator('#hot-counter-A').click();
  await page.locator('#hot-counter-B').click();
  await page.locator('#hot-draft').fill('Unsaved Java draft 🚀');
  await page.locator('#hot-reverse').click();
  await page.locator('#hot-reducer').click();
  await page.locator('#hot-record').click();
  await page.locator('#hot-model').click();
  const starts = await page.evaluate(() => globalThis.hotFixtureEvents.filter(e => e.kind === 'init-command').length);
  const result = await replace(page, {hotFixtureLabel: 'After edit'});
  expect(result.independent).toBe(true);
  expect(result.snapshot.components.length).toBe(3);
  expect(result.snapshot.stores).toHaveLength(1);
  await expect(page.locator('#hot-label')).toHaveText('After edit');
  await expect(page.locator('#hot-counter-A')).toHaveText('A: 2');
  await expect(page.locator('#hot-counter-B')).toHaveText('B: 1');
  await expect(page.locator('#hot-counters button').first()).toHaveText('B: 1');
  await expect(page.locator('#hot-draft')).toHaveValue('Unsaved Java draft 🚀');
  await expect(page.locator('#hot-reducer')).toHaveText('Reducer 3');
  await expect(page.locator('#hot-record')).toHaveText('Record 1 record');
  await expect(page.locator('#hot-model')).toHaveText('Model 1 elm');
  await expect(page.locator('#hot-ref')).toHaveText('After edit');
  expect(await page.evaluate(() => globalThis.hotFixtureEvents.filter(e => e.kind === 'init-command').length)).toBe(starts);
  expect(await page.evaluate(() => globalThis.hotFixtureEvents.some(e => e.kind === 'unsubscribe'))).toBe(true);
  await page.locator('#hot-counter-A').click();
  await page.locator('#hot-record').click();
  await page.locator('#hot-model').click();
  await expect(page.locator('#hot-counter-A')).toHaveText('A: 3');
  await expect(page.locator('#hot-record')).toHaveText('Record 2 record');
  await expect(page.locator('#hot-model')).toHaveText('Model 2 elm');
  expect(errors).toEqual([]);
});

test('incompatible component signatures and codec schemas safely reset without reusing Java objects', async ({page}) => {
  await page.goto('/hot');
  await page.locator('#hot-counter-A').click();
  await page.locator('#hot-draft').fill('Keep this compatible draft');
  await page.locator('#hot-record').click();
  await page.locator('#hot-model').click();
  await replace(page, {hotFixtureSignature: 'counter:changed:2', hotFixtureCodecVersion: '2'});
  await expect(page.locator('#hot-counter-A')).toHaveText('A: 0');
  await expect(page.locator('#hot-draft')).toHaveValue('Keep this compatible draft');
  await expect(page.locator('#hot-record')).toHaveText('Record 0 record');
  await expect(page.locator('#hot-model')).toHaveText('Model 0 elm');
  expect(await page.evaluate(() => globalThis.__JX_HOT__.notices.some(n => n.includes('signature changed')))).toBe(true);
  expect(await page.evaluate(() => globalThis.__JX_HOT__.notices.some(n => n.includes('schema')))).toBe(true);
});

test('candidate loading leaves the old tree usable and apply captures its latest committed state', async ({page}) => {
  await page.goto('/hot');
  await page.locator('#hot-counter-A').click();
  await page.evaluate(async () => {
    globalThis.hotFixtureCandidate = await import('/application.js?candidate=' + crypto.randomUUID());
    try { await import('/missing-candidate.js'); } catch { globalThis.hotFailedImportObserved = true; }
  });
  await page.locator('#hot-counter-A').click();
  await page.locator('#hot-draft').fill('Edited while candidate was loading');
  await expect(page.locator('#hot-counter-A')).toHaveText('A: 2');
  await page.evaluate(async () => {
    const hot = globalThis.__JX_HOT__;
    const snapshot = hot.capture(); hot.dispose(); hot.restore(snapshot);
    globalThis.hotFixtureCandidate.main([]);
    await hot.ready();
  });
  await expect(page.locator('#hot-counter-A')).toHaveText('A: 2');
  await expect(page.locator('#hot-draft')).toHaveValue('Edited while candidate was loading');
  expect(await page.evaluate(() => globalThis.hotFailedImportObserved)).toBe(true);
});

test('an uncaught candidate render rejects readiness and a later generation can recover the checkpoint', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/hot');
  await page.locator('#hot-counter-A').click();
  await page.locator('#hot-draft').fill('Survive a broken render');
  const rejected = await page.evaluate(async () => {
    const hot = globalThis.__JX_HOT__;
    const checkpoint = hot.capture();
    const broken = await import('/application.js?broken=' + crypto.randomUUID());
    hot.dispose(); hot.restore(checkpoint); globalThis.hotFixtureFailStartup = true;
    broken.main([]);
    const rejected = await Promise.race([
      hot.ready().then(() => false, () => true),
      new Promise((_, reject) => setTimeout(() => reject(new Error('Uncaught error did not reject readiness')), 4000))
    ]);
    const failure = hot.failure;
    hot.dispose(); hot.restore(checkpoint); globalThis.hotFixtureFailStartup = false;
    const fixed = await import('/application.js?fixed=' + crypto.randomUUID());
    fixed.main([]); await hot.ready();
    return rejected && !!failure;
  });
  expect(rejected).toBe(true);
  await expect(page.locator('#hot-counter-A')).toHaveText('A: 1');
  await expect(page.locator('#hot-draft')).toHaveValue('Survive a broken render');
  expect(errors.length).toBeGreaterThan(0);
});
