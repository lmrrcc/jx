import {test, expect} from '@playwright/test';

const events = page => page.evaluate(() => globalThis.presenceEvents || []);
const captured = page => page.evaluate(() => JSON.parse(JSON.stringify(globalThis.__JX_HOT__.capture())));
const presence = snapshot => snapshot.classes.find(value => value.signature === 'class-presence:1');
// The pinned native React warns once per constructor type for derived state
// starting at null. Independent TeaVM generations create independent types.
const initialNullWarning = '`%s` uses `getDerivedStateFromProps` but its initial state is %s. This is not recommended. Instead, define the initial state by assigning an object to `this.state` in the constructor of `%s`. This ensures that `getDerivedStateFromProps` arguments have a consistent shape. hot.ClassPresencePeer null hot.ClassPresencePeer';

async function open(page, skip = false) {
  await page.goto('/hot?fixture=class-presence&skip=' + skip);
  await expect(page.locator('#presence-status')).toHaveText('ready');
  await expect(page.locator('#presence-value')).toHaveText('null');
  await expect(page.locator('#empty-record')).toHaveText('empty-record');
  await page.evaluate(() => globalThis.__JX_HOT__.ready());
}

async function replace(page, {mode = 'valid', snapshot = null} = {}) {
  return page.evaluate(async ({mode, snapshot}) => {
    globalThis.presenceDecoderMode = mode;
    // The new Java constructor deliberately differs from the saved state.
    // Resetting to it must not masquerade as a successful null restoration.
    globalThis.presenceDifferentInitial = true;
    const next = await import('/application.js?presence-generation=' + crypto.randomUUID());
    const independent = next.main !== globalThis.hotFixtureModule.main;
    const checkpoint = snapshot || JSON.parse(JSON.stringify(globalThis.__JX_HOT__.capture()));
    globalThis.__JX_HOT__.dispose();
    globalThis.__JX_HOT__.restore(checkpoint);
    // This is actual replacement, NOT a preflight validator. Decoder failures occur
    // after disposal and must reject readiness; a repaired generation can recover.
    next.main([]);
    globalThis.hotFixtureModule = next;
    try {
      await globalThis.__JX_HOT__.ready();
      return {independent, checkpoint, status: 'ready'};
    } catch (error) {
      return {independent, checkpoint, status: 'failed', error: String(error)};
    }
  }, {mode, snapshot});
}

test('explicit class codecs preserve null aggregate empty record and every sparse presence across independent generations', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await open(page);
  for (const [operation, expected] of [[null, 'null'], ['empty', 'absent'], ['undefined', 'undefined'],
    ['null', 'present-null'], ['edit', 'value:edited:π']]) {
    if (operation) {
      await page.locator('#presence-' + operation).click();
      await expect(page.locator('#presence-value')).toHaveText(expected);
      await expect.poll(() => events(page)).toContainEqual({kind: 'callback', value: expected});
    }
    const before = await captured(page);
    expect(presence(before).payload).toBe(expected);
    expect(before.classes).toHaveLength(2);
    expect(before.classes.find(value => value.signature === 'empty-record:1').payload).toBe('empty-record');
    const old = await page.locator('#presence-value').elementHandle();
    const generation = Number(await page.locator('#presence-generation').textContent());
    const beforeReplacement = (await events(page)).length;
    const result = await replace(page);
    expect(result).toMatchObject({independent: true, status: 'ready'});
    await expect(page.locator('#presence-value')).toHaveText(expected);
    await expect(page.locator('#empty-record')).toHaveText('empty-record');
    expect((await events(page)).slice(beforeReplacement)).toContainEqual({kind: 'constructor', value: 'value:constructor'});
    expect(Number(await page.locator('#presence-generation').textContent())).toBe(generation + 1);
    expect(await page.locator('#presence-value').evaluate((node, previous) => node === previous, old)).toBe(false);
    await old.dispose();
  }
  await page.locator('#presence-undefined').click();
  await expect(page.locator('#presence-value')).toHaveText('undefined');
  // Initial mount and the first replacement both begin at null. Every other
  // restored state is an object, so no additional warning is expected.
  expect(errors).toEqual([initialNullWarning, initialNullWarning]);
});

test('nullable and sparse checkpoints preserve native skipped callbacks and committed props derivation', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  const warnings = []; page.on('console', message => { if (message.type() === 'error') warnings.push(message.text()); });
  await open(page, true);
  const offset = (await events(page)).length;
  await page.locator('#presence-undefined').click();
  await expect.poll(() => events(page)).toContainEqual({kind: 'callback', value: 'undefined'});
  await expect(page.locator('#presence-value')).toHaveText('null');
  expect(presence(await captured(page)).payload).toBe('undefined');
  expect((await events(page)).slice(offset).some(value => value.kind === 'render')).toBe(false);
  expect((await replace(page)).status).toBe('ready');
  await expect(page.locator('#presence-value')).toHaveText('undefined');
  const derivedOffset = (await events(page)).length;
  await page.locator('#presence-derived').click();
  await expect.poll(() => events(page)).toContainEqual({kind: 'should', value: 'value:derived'});
  await expect(page.locator('#presence-value')).toHaveText('undefined');
  expect((await events(page)).slice(derivedOffset).some(value => ['render', 'commit', 'callback'].includes(value.kind))).toBe(false);
  expect(presence(await captured(page)).payload).toBe('value:derived');
  expect((await replace(page)).status).toBe('ready');
  await expect(page.locator('#presence-value')).toHaveText('value:derived');
  expect(errors).toEqual([]);
  expect(warnings).toEqual([initialNullWarning]);
});

for (const mode of ['throw', 'invalid']) {
  test('a candidate decoder that is ' + mode + ' fails readiness and a corrected generation recovers the retained checkpoint', async ({page}) => {
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    await open(page);
    await page.locator('#presence-edit').click();
    await expect(page.locator('#presence-value')).toHaveText('value:edited:π');
    const old = await page.locator('#presence-value').elementHandle();
    const failed = await replace(page, {mode});
    expect(failed.status).toBe('failed');
    const expectedError = mode === 'throw' ? 'Candidate presence decoder rejected payload' : 'Field presence must be non-null';
    expect(failed.error).toContain(expectedError);
    expect(presence(failed.checkpoint).payload).toBe('value:edited:π');
    expect(await old.evaluate(node => node.isConnected)).toBe(false);
    await old.dispose();
    await expect.poll(() => errors.join('\n')).toContain(expectedError);
    await expect(page.locator('#presence-value')).toHaveCount(0);
    const recovered = await replace(page, {snapshot: failed.checkpoint});
    expect(recovered).toMatchObject({independent: true, status: 'ready'});
    await expect(page.locator('#presence-value')).toHaveText('value:edited:π');
    await page.locator('#presence-null').click();
    await expect(page.locator('#presence-value')).toHaveText('present-null');
  });
}

test('malformed declared codec payload fails instead of silently restoring null state', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await open(page);
  const saved = await captured(page);
  const invalid = structuredClone(saved);
  presence(invalid).payload = 'unrecognized-payload';
  const result = await replace(page, {snapshot: invalid});
  expect(result.status).toBe('failed');
  expect(result.error).toContain('Malformed presence checkpoint');
  await expect.poll(() => errors.join('\n')).toContain('Malformed presence checkpoint');
  await expect(page.locator('#presence-value')).toHaveCount(0);
  expect((await replace(page, {snapshot: saved})).status).toBe('ready');
  await expect(page.locator('#presence-value')).toHaveText('null');
});
