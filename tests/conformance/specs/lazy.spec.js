import {test, expect} from '@playwright/test';
import {readFileSync} from 'node:fs';
import {posix} from 'node:path';

const routeRequest = url => new URL(url).pathname.endsWith('/route.js');

/** Follow static output imports, including shared chunks, rather than trusting an entry filename. */
function staticInputs(metadata, entry) {
  const outputs = metadata.outputs;
  const root = Object.keys(outputs).find(path => posix.basename(path.replaceAll('\\', '/')) === entry);
  expect(root, 'the lazy build must contain ' + entry).toBeDefined();
  const pending = [root];
  const visited = new Set();
  const inputs = new Set();
  while (pending.length) {
    const current = pending.pop();
    if (visited.has(current)) continue;
    visited.add(current);
    const output = outputs[current];
    expect(output, 'every static output import must resolve: ' + current).toBeDefined();
    for (const [input, contribution] of Object.entries(output.inputs || {})) {
      if (contribution.bytesInOutput > 0) inputs.add(input.replaceAll('\\', '/'));
    }
    for (const imported of output.imports || []) {
      if (imported.external || imported.kind === 'dynamic-import') continue;
      const path = imported.path.replaceAll('\\', '/');
      pending.push(Object.hasOwn(outputs, path) ? path : posix.normalize(posix.join(posix.dirname(current), path)));
    }
  }
  return inputs;
}

/** esbuild sees each TeaVM program as one input; inspect its original Java sources as well. */
function conformanceSources(compilation) {
  const mapping = JSON.parse(readFileSync(new URL('../target/' + compilation + '/application.js.map', import.meta.url), 'utf8'));
  return [...new Set(mapping.sources.flatMap(source => {
    const match = /(?:^|\/)jx\/conformance\/(.+\.java)$/.exec(source.replaceAll('\\', '/'));
    // Some mappings name inner/synthetic classes separately; their enclosing source owns them.
    return match ? [match[1].replace(/\$[^/]*\.java$/, '.java')] : [];
  }))];
}

function assertCasesAreDeferred(info) {
  const side = info.project.name.split('-')[0];
  const metadata = JSON.parse(readFileSync(new URL('../target/' + side + '-lazy/metafile.json', import.meta.url), 'utf8'));
  const isCaseInput = input => /(?:^|\/)reference\/cases(?:\.jsx$|\/)/.test(input)
    || input.endsWith('/conformance/target/lazy-route/application.js');
  expect([...staticInputs(metadata, 'host.js')].filter(isCaseInput),
    'the initial host and all its static shared chunks must contain no case implementation').toEqual([]);
  expect([...staticInputs(metadata, 'route.js')].some(isCaseInput),
    'the delayed route must actually contain the case implementation').toBe(true);
  if (side === 'jx') {
    const host = conformanceSources('lazy-host');
    const allowed = new Set(['LazyHost.java', 'LazyHostPage.java', 'Bench.java']);
    expect(host, 'the source map must identify the compiled host').toEqual(expect.arrayContaining([
      'LazyHost.java', 'LazyHostPage.java'
    ]));
    expect(host.filter(source => !allowed.has(source)),
      'TeaVM must not compile the case registry, cases or their helpers into the initial host').toEqual([]);
    expect(conformanceSources('lazy-route'),
      'the delayed TeaVM program must contain the registry and the exercised case').toEqual(expect.arrayContaining([
      'Main.java', 'cases/StateCounter.java'
    ]));
  }
}

async function assertWorkingCounter(page) {
  await expect.poll(() => page.evaluate(() => globalThis.__benchLazyReady === true)).toBe(true);
  await expect(page.getByTestId('bench-route-loading')).toHaveCount(0);
  await expect(page.getByTestId('bench-route-error')).toHaveCount(0);
  await expect(page.getByRole('status')).toHaveText('0');
  await page.getByRole('button', {name: 'Increment', exact: true}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  await page.getByRole('button', {name: 'Add three', exact: true}).click();
  await expect(page.getByRole('status')).toHaveText('4');
}

test('lazy-route-loading: case code is fetched only after opening the route and commits after loading', async ({page}, info) => {
  test.skip(!info.project.name.endsWith('-lazy'), 'this scenario exercises the lazy-route host');
  assertCasesAreDeferred(info);
  const problems = [];
  page.on('pageerror', error => problems.push(error.message));
  page.on('console', message => { if (message.type() === 'error') problems.push(message.text()); });
  let requests = 0;
  let release;
  const download = new Promise(resolve => { release = resolve; });
  await page.route(routeRequest, async route => {
    requests++;
    await download;
    await route.continue();
  });
  try {
    await page.goto('./?case=state-counter&mode=lazy');
    await expect(page.getByTestId('bench-open-route')).toBeVisible();
    expect(requests).toBe(0);
    expect(await page.evaluate(() => globalThis.__benchLazyReady === true)).toBe(false);
    await expect(page.getByRole('button', {name: 'Increment', exact: true})).toHaveCount(0);

    await page.getByTestId('bench-open-route').click();
    await expect.poll(() => requests).toBe(1);
    await expect(page.getByTestId('bench-route-loading')).toBeVisible();
    expect(await page.evaluate(() => globalThis.__benchLazyReady === true)).toBe(false);
    await expect(page.getByRole('button', {name: 'Increment', exact: true})).toHaveCount(0);

    release();
    await assertWorkingCounter(page);
    expect(requests).toBe(1);
    expect(problems).toEqual([]);
  } finally {
    // A failed assertion must not leave an intercepted request awaiting a promise forever.
    release();
  }
});

test('lazy-route-retry: a failed download is reported and explicit retry preserves the existing root container', async ({page}, info) => {
  test.skip(!info.project.name.endsWith('-lazy'), 'this scenario exercises the lazy-route host');
  let requests = 0;
  let fail = true;
  await page.route(routeRequest, route => {
    requests++;
    return fail ? route.abort() : route.continue();
  });
  await page.goto('./?case=state-counter&mode=lazy');
  await expect(page.getByTestId('bench-open-route')).toBeVisible();
  expect(requests).toBe(0);
  const root = await page.locator('#app').elementHandle();
  expect(root).not.toBeNull();
  await page.getByTestId('bench-open-route').click();
  await expect(page.getByTestId('bench-route-error')).toBeVisible();
  expect(requests).toBe(1);
  expect(await page.evaluate(() => globalThis.__benchLazyReady === true)).toBe(false);
  await expect(page.getByRole('button', {name: 'Increment', exact: true})).toHaveCount(0);

  // Keep the interception installed: the successful retry must make a second real request.
  fail = false;
  await page.getByTestId('bench-route-retry').click();
  await assertWorkingCounter(page);
  expect(requests).toBe(2);
  expect(await root.evaluate(element => element.isConnected && element === document.getElementById('app'))).toBe(true);
  await root.dispose();
});
