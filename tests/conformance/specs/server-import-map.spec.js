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
  page.on('request', request => {
    if (['/blocked', '/maps/should-not-load.mjs', '/maps/fallback.mjs'].includes(new URL(request.url()).pathname))
      blockedRequests.push(request.url());
  });
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
    body: `import {value} from 'shared';
      try { await import('scoped-blocked'); globalThis.__scopedBlocked = false; }
      catch (error) { globalThis.__scopedBlocked = error instanceof TypeError; }
      export default value;`}));
  await page.route('**/maps/**', route => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith('/global.mjs') ? source : path.endsWith('/scoped.mjs')
      ? "export const value = 'scoped';" : path.endsWith('/pkg/value.mjs') ? "export default 'prefixed';"
      : path.endsWith('/proto.mjs') ? "export default 'proto';" : path.endsWith('/constructor.mjs')
      ? "export default 'constructor';" : path.endsWith('/empty.mjs') ? "export default 'empty-scope';"
      : "throw new Error('Unexpected module request');";
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
    await expect.poll(() => page.evaluate(() => globalThis.__emptyScope)).toBe('empty-scope');
    await expect.poll(() => page.evaluate(() => globalThis.__blocked)).toBe(true);
    expect(await page.evaluate(() => globalThis.__scopedBlocked)).toBe(true);
    expect(await page.evaluate(() => globalThis.__injected)).toBeUndefined();
    const map = await page.locator('script[type="importmap"]').evaluate(node => {
      const value = JSON.parse(node.textContent);
      return {hasProto: Object.hasOwn(value.imports, '__proto__'), proto: value.imports.__proto__,
        blocked: value.imports.blocked, emptyScope: value.scopes['']['empty-shared'],
        nonce: node.nonce,
        hasEscaped: Object.hasOwn(value.imports, 'escaped</script><script>globalThis.__injected=1</script>')};
    });
    expect(map.hasProto).toBe(true);
    expect(map.proto).toBe('/maps/proto.mjs');
    expect(map.blocked).toBeNull();
    expect(map.emptyScope).toBe('/maps/empty.mjs');
    expect(map.hasEscaped).toBe(true);
    expect(map.nonce).toBe(kind.startsWith('prerender') ? '' : 'first');
    expect(errors).toEqual([]);
    // A null target is the standard resolution model's explicit block. Chrome warns
    // about its authoring shape; verify that precise warning rather than hiding it.
    expect(warnings).toHaveLength(2);
    expect(warnings.every(warning => warning.includes('blocked'))).toBe(true);
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
