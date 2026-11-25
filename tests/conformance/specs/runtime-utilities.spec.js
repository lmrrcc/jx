import {readFileSync} from 'node:fs';
import {test, expect, server as onServer, sideOf} from './bench.js';

const pinned = JSON.parse(readFileSync(new URL('../../../package.json', import.meta.url), 'utf8')).dependencies;

test('runtime-utilities: actual runtime versions, owner stack and legacy batching preserve their contracts', async ({page, open}) => {
  await open('runtime-utilities');
  await expect(page.getByTestId('react-version')).toHaveText(pinned.react);
  await expect(page.getByTestId('dom-version')).toHaveText(pinned['react-dom']);
  await expect(page.getByTestId('client-version')).toHaveText(pinned['react-dom']);
  await expect(page.getByTestId('owner-stack')).toHaveText('present');
  // An empty development string is valid. The explicit interop owner provides a
  // short real creation stack whose name must survive the Java diagnostics bridge.
  await expect(page.getByTestId('interop-owner-stack')).toHaveText('present');
  await expect(page.getByTestId('interop-owner-stack')).toHaveAttribute('title', /NativeOwner/);
  await page.getByRole('button', {name: 'Batch', exact: true}).click();
  await expect(page.getByTestId('batch-result')).toHaveText('2:same');
  await page.getByRole('button', {name: 'Fail batch'}).click();
  await expect(page.getByTestId('batch-result')).toHaveText('2:batch failed');
});

onServer('server-runtime-versions: all five version getters match the actual pinned exports in Node', async api => {
  expect(api.reactVersion()).toBe(pinned.react);
  for (const name of ['domVersion', 'clientVersion', 'serverVersion', 'staticVersion']) {
    expect(api[name]()).toBe(pinned['react-dom']);
  }
});

test('runtime-utilities-production: genuine production bundles omit owner stacks and reject act without running its callback', async ({page}, info) => {
  const manifest = JSON.parse(readFileSync(new URL('../target/' + sideOf(info) + '-production/metafile.json', import.meta.url), 'utf8'));
  const inputs = Object.keys(manifest.inputs);
  expect(inputs.some(path => path.endsWith('react/cjs/react.production.js'))).toBe(true);
  expect(inputs.some(path => path.endsWith('react-dom/cjs/react-dom-client.production.js'))).toBe(true);
  expect(inputs.filter(path => path.endsWith('.development.js'))).toEqual([]);
  const root = '../' + sideOf(info) + '-production/';
  await page.goto(root + '?case=runtime-utilities');
  await expect(page.getByTestId('owner-stack')).toHaveText('absent');
  await expect(page.getByTestId('owner-stack')).toHaveAttribute('title', '');
  await expect(page.getByTestId('interop-owner-stack')).toHaveText('absent');
  await expect(page.getByTestId('react-version')).toHaveText(pinned.react);
  await expect(page.getByTestId('dom-version')).toHaveText(pinned['react-dom']);
  await expect(page.getByTestId('client-version')).toHaveText(pinned['react-dom']);
  await page.getByRole('button', {name: 'Batch', exact: true}).click();
  await expect(page.getByTestId('batch-result')).toHaveText('2:same');
  await page.goto(root + '?case=act-utilities');
  await expect(page.locator('#app')).toHaveAttribute('data-bench', 'act-utilities');
  const result = await page.evaluate(async () => {
    const api = globalThis.__benchUtilities;
    try { await api.mount(); return {status: 'fulfilled'}; }
    catch (error) { return {status: 'rejected', message: error.message, calls: api.calls(), outside: api.outside}; }
  });
  expect(result).toEqual({status: 'rejected', message: 'React act requires a development build', calls: 0, outside: null});
});
