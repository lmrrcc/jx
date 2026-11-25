import {test, expect} from '@playwright/test';
import {readFile, writeFile} from 'node:fs/promises';

const events = page => page.evaluate(() => globalThis.hotClassEvents || []);
async function open(page, skip = false, portal = false) {
  await page.goto('/hot?fixture=class-journal&skip=' + skip + '&portal=' + portal);
  await expect(page.locator('#class-value')).toHaveText('0');
  await page.evaluate(() => globalThis.__JX_HOT__.ready());
}
async function replace(page, changes = {}) {
  return page.evaluate(async changes => {
    Object.assign(globalThis, changes);
    const next = await import('/application.js?class-generation=' + crypto.randomUUID());
    const independent = next.main !== globalThis.hotFixtureModule.main;
    // Capture must succeed before any old root/resources are disposed.
    const snapshot = JSON.parse(JSON.stringify(globalThis.__JX_HOT__.capture()));
    globalThis.__JX_HOT__.dispose();
    globalThis.__JX_HOT__.restore(snapshot);
    next.main([]);
    globalThis.hotFixtureModule = next;
    await globalThis.__JX_HOT__.ready();
    return {independent, snapshot};
  }, changes);
}
const captured = page => page.evaluate(() => {
  try { return {snapshot: JSON.parse(JSON.stringify(globalThis.__JX_HOT__.capture()))}; }
  catch (error) { return {error: error.message}; }
});

test('nullable and sparse checkpoint descriptors reject before touching the live root', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await open(page);
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('1');
  const original = await page.locator('#class-value').elementHandle();
  const attempts = await page.evaluate(() => {
    const calls = [], hot = globalThis.__JX_HOT__;
    const capture = hot.capture, dispose = hot.dispose;
    hot.capture = (...args) => { calls.push('capture'); return capture(...args); };
    hot.dispose = (...args) => { calls.push('dispose'); return dispose(...args); };
    try {
      return {calls, nullableFirst: globalThis.hotClassAdmission('nullable-first'),
        checkpointFirst: globalThis.hotClassAdmission('checkpoint-first'),
        optional: globalThis.hotClassAdmission('optional-state')};
    } finally { hot.capture = capture; hot.dispose = dispose; }
  });
  expect(attempts.nullableFirst).toContain('nullable');
  expect(attempts.checkpointFirst).toContain('nullable');
  expect(attempts.optional).toContain('optional');
  expect(attempts.calls).toEqual([]);
  expect(await page.locator('#class-value').evaluate((node, old) => node === old, original)).toBe(true);
  await original.dispose();
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('2');
  expect((await captured(page)).snapshot.classes[0].payload).toBe('2');
  expect(errors).toEqual([]);
});

test('visible class commits restore through typed strings into an independent TeaVM generation', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await open(page);
  await page.locator('#class-add').click();
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('2');
  const result = await replace(page, {hotClassLabel: 'Second generation'});
  expect(result.independent).toBe(true);
  expect(result.snapshot.classes).toHaveLength(1);
  expect(result.snapshot.classes[0]).toMatchObject({signature: 'class-count:1', schema: 'fixture:class-count:1', payload: '2'});
  await expect(page.locator('#class-generation')).toHaveText('Second generation');
  await expect(page.locator('#class-value')).toHaveText('2');
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('3');
  expect(errors).toEqual([]);
});

test('a visible skipped render is journaled by its own native update callback', async ({page}) => {
  await open(page, true);
  await page.locator('#class-add').click();
  await expect.poll(() => events(page)).toContainEqual({kind: 'callback', value: 1});
  await expect(page.locator('#class-value')).toHaveText('0');
  const result = await replace(page);
  expect(result.independent).toBe(true);
  expect(result.snapshot.classes[0].payload).toBe('1');
  await expect(page.locator('#class-value')).toHaveText('1');
});

test('hidden skipped updates reject replacement rather than restore the old journal', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await open(page, true);
  const native = await page.evaluate(() => globalThis.hotClassNativeProbe());
  const controlPath = test.info().outputPath('native-hidden-class-control.json');
  await writeFile(controlPath, JSON.stringify(native));
  await test.info().attach('native-hidden-class-control', {path: controlPath, contentType: 'application/json'});
  const original = await page.locator('#class-value').elementHandle();
  await page.locator('#class-hide').click();
  await expect(page.locator('#class-value')).toBeHidden();
  const offset = (await events(page)).length;
  await page.evaluate(() => globalThis.hotClassAct('increment-retained'));
  expect((await events(page)).slice(offset)).not.toContainEqual({kind: 'callback', value: 1});
  expect((await captured(page)).error).toContain('pending native updates');
  await expect(replace(page)).rejects.toThrow('Class checkpoint capture unqualified');
  expect(await page.locator('#class-value').evaluate((node, old) => node === old, original)).toBe(true);
  await original.dispose();
  await expect(page.locator('#class-generation')).toHaveText('First generation');
  await page.locator('#class-show').click();
  await expect.poll(() => events(page)).toContainEqual({kind: 'mount', value: 1});
  expect(errors).toEqual([]);
  const callback = {kind: 'callback', value: 1};
  expect(native.hidden).not.toContainEqual(callback);
  if (native.shown.some(event => event.kind === 'callback'))
    await expect.poll(() => events(page)).toContainEqual(callback);
  else expect(await events(page)).not.toContainEqual(callback);
});

test('props-only derivation with a skipped render cannot promote a candidate', async ({page}) => {
  await open(page, true);
  await page.locator('#class-derived').click();
  await expect.poll(() => events(page)).toContainEqual({kind: 'should', value: 2});
  await expect(page.locator('#class-value')).toHaveText('0');
  expect((await captured(page)).error).toContain('unmatched render candidates');
});

test('an abandoned class candidate and urgent retained descendant commit cannot promote speculative state', async ({page}) => {
  await open(page);
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('1');
  const original = await page.locator('#class-value').elementHandle();
  await page.locator('#class-suspend').click();
  await expect.poll(() => events(page)).toContainEqual({kind: 'render', value: 99});
  await page.locator('#class-descendant').click();
  await expect(page.locator('#class-descendant')).toHaveText('Descendant 1');
  await expect(page.locator('#class-value')).toHaveText('1');
  expect(await events(page)).not.toContainEqual({kind: 'commit', value: 99});
  expect((await captured(page)).error).toContain('Class checkpoint capture unqualified');
  await expect(replace(page)).rejects.toThrow('Class checkpoint capture unqualified');
  await expect(page.locator('#class-value')).toHaveText('1');
  expect(await page.locator('#class-value').evaluate((node, old) => node === old, original)).toBe(true);
  await original.dispose();
});

test('real deletion while hidden removes an unqualified owner and does not resurrect its state', async ({page}) => {
  await open(page, true);
  await page.locator('#class-hide').click();
  await page.evaluate(() => globalThis.hotClassAct('increment-retained'));
  expect((await captured(page)).error).toContain('pending native updates');
  await page.locator('#class-remove').click();
  await expect(page.locator('#class-value')).toHaveCount(0);
  await expect.poll(async () => (await captured(page)).snapshot?.classes).toEqual([]);
  expect((await replace(page)).independent).toBe(true);
  await page.locator('#class-show').click();
  await page.locator('#class-remount').click();
  await expect(page.locator('#class-value')).toHaveText('0');
});

test('codec failure blocks replacement while application callbacks and state remain live', async ({page}) => {
  await open(page);
  await page.evaluate(() => { globalThis.hotClassFailEncoding = true; });
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('1');
  await expect.poll(() => events(page)).toContainEqual({kind: 'callback', value: 1});
  expect((await captured(page)).error).toContain('Class checkpoint encoding failed');
  await expect(replace(page)).rejects.toThrow('Class checkpoint encoding failed');
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('2');
});

test('a deliberate class schema change resets only the declared state in a fresh generation', async ({page}) => {
  await open(page);
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('1');
  const result = await replace(page, {hotClassSchema: '2'});
  expect(result.independent).toBe(true);
  expect(result.snapshot.classes[0].schema).toBe('fixture:class-count:1');
  await expect(page.locator('#class-value')).toHaveText('0');
  expect(await page.evaluate(() => globalThis.__JX_HOT__.notices)).toContainEqual(expect.stringContaining('Class state reset: checkpoint signature/schema changed'));
});

test('malformed or oversized class checkpoints fail validation before changing incoming ownership', async ({page}) => {
  await open(page);
  const result = await page.evaluate(() => {
    const hot = globalThis.__JX_HOT__, original = hot.capture(), errors = [];
    const invalid = [
      {...original, classes: 'invalid'},
      {...original, classes: [original.classes[0], original.classes[0]]},
      {...original, classes: [{...original.classes[0], payload: 'x'.repeat(1048577)}]},
      {...original, classes: [{...original.classes[0], signature: ''}]}
    ];
    for (const snapshot of invalid) {
      try { hot.restore(snapshot); errors.push('accepted'); }
      catch (error) { errors.push(error.message); }
    }
    return {errors, original, after: hot.capture()};
  });
  expect(result.errors).toHaveLength(4);
  expect(result.errors.every(error => error.includes('Invalid JX class checkpoint'))).toBe(true);
  expect(result.after).toEqual(result.original);
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('1');
});

test('reading a candidate runtime attestation does not initialize its application entry class', async ({page}) => {
  await open(page);
  const original = await page.locator('#class-value').elementHandle();
  const result = await page.evaluate(async () => {
    const before = globalThis.runtimeApplicationInitializations;
    const candidate = await import('/application.js?preflight-only=' + crypto.randomUUID());
    return {before, after: globalThis.runtimeApplicationInitializations,
      protocol: candidate.hotRuntimeVersion, independent: candidate.main !== globalThis.hotFixtureModule.main};
  });
  expect(result).toEqual({before: 1, after: 1, protocol: 2, independent: true});
  expect(await page.locator('#class-value').evaluate((node, old) => node === old, original)).toBe(true);
  await original.dispose();
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('1');
});

test('an older active runtime rejects the new candidate during evaluation before touching its DOM', async ({page}) => {
  await open(page);
  await page.locator('#class-add').click();
  const original = await page.locator('#class-value').elementHandle();
  const failure = await page.evaluate(async () => {
    const hot = globalThis.__JX_HOT__, previous = hot.version;
    hot.version = 1; // exact old runtime protocol; guard must run before candidate main.
    try { await import('/application.js?old-protocol=' + crypto.randomUUID()); return 'accepted'; }
    catch (error) { return error.message; }
    finally { hot.version = previous; }
  });
  expect(failure).toContain('load a fresh development page');
  expect(await page.locator('#class-value').evaluate((node, old) => node === old, original)).toBe(true);
  await original.dispose();
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('2');
});

test('the actual development loader rejects an old bundle before capture or disposal', async ({page}) => {
  await page.route('**/protocol-host', route => route.fulfill({contentType: 'text/html', body: '<button id="old">Old application</button>'}));
  await page.route('**/__jx_dev/version', route => route.fulfill({json: {version: 2, session: 'probe', state: 'ready'}}));
  await page.route('**/application.js?*', route => route.fulfill({contentType: 'text/javascript', body:
    'globalThis.__JX_BUNDLE__ = { main() { globalThis.protocolCalls.push("main"); } };'}));
  await page.goto('/protocol-host');
  const original = await page.locator('#old').elementHandle();
  const client = (await readFile(new URL('../../../jx-maven-plugin/src/main/resources/jx-dev-client.js', import.meta.url), 'utf8'))
    .replace('__JX_INITIAL_STATUS__', JSON.stringify({version: 1, session: 'probe', state: 'ready'}));
  await page.addScriptTag({content: client + '\n globalThis.protocolCalls = []; Object.assign(globalThis.__JX_HOT__, {'
    + 'version:2, capture(){protocolCalls.push("capture");}, dispose(){protocolCalls.push("dispose");}});'});
  await expect(page.locator('#__jx_dev_notice')).toContainText('load a fresh development page');
  expect(await page.evaluate(() => globalThis.protocolCalls)).toEqual([]);
  expect(await page.locator('#old').evaluate((node, old) => node === old, original)).toBe(true);
  await original.dispose();
});

test('an incoming owner omitted before initial commit fails readiness and cannot resurrect on later insertion', async ({page}) => {
  await open(page);
  await page.locator('#class-add').click();
  await expect(page.locator('#class-value')).toHaveText('1');
  await expect(replace(page, {hotClassOmit: true})).rejects.toThrow('incoming owners did not commit');
  expect((await captured(page)).error).toContain('incoming class owners have not committed');
  await expect(page.locator('#class-value')).toHaveCount(0);
  await page.evaluate(() => { globalThis.hotClassOmit = false; });
  await page.locator('#class-remove').click();
  await page.locator('#class-remount').click();
  await expect(page.locator('#class-value')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => globalThis.__JX_HOT__.failure))
    .toContain('owner did not commit in the initial tree');
  expect((await captured(page)).error).toContain('incoming class owners have not committed');
});

test('a checkpointed class in an external React root rejects capture even while one JX root exists', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await open(page);
  await page.evaluate(() => globalThis.hotClassExternalMount());
  const owned = page.locator('#root'), external = page.locator('#external-hot-root');
  await expect(external.locator('#class-value')).toHaveText('0');
  await owned.locator('#class-add').click();
  await external.locator('#class-add').click();
  await expect(owned.locator('#class-value')).toHaveText('1');
  await expect(external.locator('#class-value')).toHaveText('1');
  const ownedNode = await owned.locator('#class-value').elementHandle();
  const externalNode = await external.locator('#class-value').elementHandle();
  expect((await captured(page)).error).toMatch(/outside|external|owned root/i);
  await expect(replace(page)).rejects.toThrow(/outside|external|owned root/i);
  expect(await owned.locator('#class-value').evaluate((node, old) => node === old, ownedNode)).toBe(true);
  expect(await external.locator('#class-value').evaluate((node, old) => node === old, externalNode)).toBe(true);
  await ownedNode.dispose(); await externalNode.dispose();
  await owned.locator('#class-add').click();
  await external.locator('#class-add').click();
  await expect(owned.locator('#class-value')).toHaveText('2');
  await expect(external.locator('#class-value')).toHaveText('2');
  expect(errors).toEqual([]);
  // Removing the external owner must clear its rejection without capturing its state.
  await page.evaluate(() => globalThis.hotClassExternalUnmount());
  await expect(external).toHaveCount(0);
  await expect.poll(async () => (await captured(page)).snapshot?.classes?.length).toBe(1);
  expect((await captured(page)).snapshot.classes[0].payload).toBe('2');
});

test('a portal outside the root DOM retains the JX root owner and restores its committed class state', async ({page}) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await open(page, false, true);
  const portal = page.locator('#class-portal');
  await expect(portal).toHaveCount(1);
  expect(await portal.evaluate(node => !document.getElementById('root').contains(node))).toBe(true);
  await expect(page.locator('#root #class-value')).toHaveCount(0);
  await portal.locator('#class-add').click();
  await expect(portal.locator('#class-value')).toHaveText('1');
  const result = await replace(page, {hotClassLabel: 'Portal generation'});
  expect(result.independent).toBe(true);
  expect(result.snapshot.classes).toHaveLength(1);
  expect(result.snapshot.classes[0].payload).toBe('1');
  await expect(page.locator('#root #class-generation')).toHaveText('Portal generation');
  await expect(portal).toHaveCount(1);
  await expect(portal.locator('#class-value')).toHaveText('1');
  await portal.locator('#class-add').click();
  await expect(portal.locator('#class-value')).toHaveText('2');
  expect(errors).toEqual([]);
});
