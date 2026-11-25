import {createHash} from 'node:crypto';
import {test, expect} from '@playwright/test';
import {modeOf, sideOf, serverOf} from './bench.js';

test('server-import-map: Java module scopes, blocked imports, escaping and integrity match React', async ({page}, info) => {
  test.skip(modeOf(info) !== 'client', 'complete import-map documents execute their own modules without a bench root');
  const server = await serverOf(info), reference = await serverOf({project: {name: 'reference'}});
  const source = "export const value = 'global';";
  const integrity = 'sha256-' + createHash('sha256').update(source).digest('base64');
  const errors = [], warnings = [], blockedRequests = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error') errors.push(message.text());
    if (message.type() === 'warning') warnings.push(message.text());
  });
  page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/blocked')) blockedRequests.push(request.url()); });
  await page.route('**/map-entry.mjs', route => route.fulfill({contentType: 'text/javascript', body: `
    import {value as global} from 'shared';
    import scoped from '/scope/child.mjs';
    import prefixed from 'pkg/value.mjs';
    import proto from '__proto__';
    import constructor from 'constructor';
    globalThis.__mapResult = [global, scoped, prefixed, proto, constructor];
    try { await import('blocked'); globalThis.__blocked = false; }
    catch (error) { globalThis.__blocked = error instanceof TypeError; }
  `}));
  await page.route('**/scope/child.mjs', route => route.fulfill({contentType: 'text/javascript',
    body: "import {value} from 'shared'; export default value;"}));
  await page.route('**/maps/**', route => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith('/global.mjs') ? source : path.endsWith('/scoped.mjs')
      ? "export const value = 'scoped';" : path.endsWith('/pkg/value.mjs') ? "export default 'prefixed';"
      : path.endsWith('/proto.mjs') ? "export default 'proto';" : path.endsWith('/constructor.mjs')
      ? "export default 'constructor';" : "throw new Error('Unexpected module request');";
    return route.fulfill({contentType: 'text/javascript', body});
  });
  for (const kind of ['readable', 'pipeable', 'prerender', 'prerender-node']) {
    const [first, second] = await Promise.all([
      server.renderImportMap(kind, 'first', integrity), server.renderImportMap(kind, 'second', integrity)
    ]);
    if (sideOf(info) === 'jx') {
      expect(first).toBe(await reference.renderImportMap(kind, 'first', integrity));
      expect(second).toBe(await reference.renderImportMap(kind, 'second', integrity));
    }
    expect(first).toContain('/maps/first/global.mjs');
    expect(first).not.toContain('/maps/second/');
    expect(second).toBe(first.replaceAll('first', 'second'));
    expect(first).not.toContain('</script><script>globalThis.__injected=1</script>');
    expect(first.indexOf('type="importmap"')).toBeLessThan(first.indexOf('type="module"'));
    const url = new URL('/map-document-' + kind, info.project.use.baseURL).href;
    await page.route(url, route => route.fulfill({contentType: 'text/html', body: first}));
    errors.length = 0; warnings.length = 0;
    await page.goto(url);
    await expect.poll(() => page.evaluate(() => globalThis.__mapResult)).toEqual(['global', 'scoped', 'prefixed', 'proto', 'constructor']);
    await expect.poll(() => page.evaluate(() => globalThis.__blocked)).toBe(true);
    expect(await page.evaluate(() => globalThis.__injected)).toBeUndefined();
    const map = await page.locator('script[type="importmap"]').evaluate(node => ({value: JSON.parse(node.textContent), nonce: node.nonce}));
    expect(Object.hasOwn(map.value.imports, '__proto__')).toBe(true);
    expect(map.value.imports.__proto__).toBe('/maps/proto.mjs');
    expect(map.value.imports.blocked).toBeNull();
    expect(Object.hasOwn(map.value.imports, 'escaped</script><script>globalThis.__injected=1</script>')).toBe(true);
    expect(map.nonce).toBe(kind.startsWith('prerender') ? '' : 'first');
    expect(errors).toEqual([]);
    // A null target is the standard resolution model's explicit block. Chrome warns
    // about its authoring shape; verify that precise warning rather than hiding it.
    expect(warnings).toHaveLength(1);
    expect(warnings[0]).toContain('blocked');
    expect(blockedRequests).toEqual([]);
  }
  const denied = new URL('/map-integrity-denied', info.project.use.baseURL).href;
  const html = await server.renderImportMap('readable', 'first', integrity);
  await page.route('**/maps/first/global.mjs', route => route.fulfill({contentType: 'text/javascript', body: source + '\n// changed bytes'}));
  await page.route(denied, route => route.fulfill({contentType: 'text/html', body: html}));
  errors.length = 0;
  await page.goto(denied);
  await expect.poll(() => errors.join('\n')).toContain('integrity');
  expect(await page.evaluate(() => globalThis.__mapResult)).toBeUndefined();
});
