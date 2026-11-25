import {test, expect} from '@playwright/test';
import {modeOf, sideOf, serverOf} from './bench.js';

test('server-bootstrap: per-request script/style CSP and inline terminator escaping match native React', async ({page}, info) => {
  test.skip(modeOf(info) !== 'client', 'this case serves complete server output with CSP, without a bench root or lazy route');
  const server = await serverOf(info);
  const reference = await serverOf({project: {name: 'reference'}});
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await page.route('**/bootstrap-classic.js', route => route.fulfill({contentType: 'text/javascript', body: 'globalThis.__classic = (globalThis.__classic || 0) + 1;'}));
  await page.route('**/bootstrap-module.js', route => route.fulfill({contentType: 'text/javascript', body: 'globalThis.__module = (globalThis.__module || 0) + 1;'}));
  for (const kind of ['readable', 'pipeable', 'prerender', 'prerender-node']) {
    for (const mode of kind.startsWith('prerender') ? ['none'] : ['none', 'script', 'style', 'split']) {
      const [html, other] = await Promise.all([
        server.renderBootstrap(kind, mode, 'first'), server.renderBootstrap(kind, mode, 'second')
      ]);
      if (sideOf(info) === 'jx') expect(html).toBe(await reference.renderBootstrap(kind, mode, 'first'));
      expect(html).not.toContain('second-');
      expect(other).not.toContain('first-');
      const scriptNonce = mode === 'script' || mode === 'split';
      const styleNonce = mode === 'style' || mode === 'split';
      expect(html.includes('nonce="first-script"')).toBe(scriptNonce);
      expect(html.includes('nonce="first-style"')).toBe(styleNonce);
      expect(html).not.toContain('</script><script>globalThis.__injected=1</script>');
      const scripts = html.match(/<script\b[^>]*>/g) || [];
      expect(scripts).toHaveLength(3);
      for (const tag of scripts) expect(tag.includes('nonce="first-script"')).toBe(scriptNonce);
      if (mode !== 'split') continue;
      errors.length = 0;
      const url = new URL('/bootstrap-proof-' + kind, info.project.use.baseURL).href;
      await page.route(url, route => route.fulfill({contentType: 'text/html', headers: {
        'content-security-policy': "default-src 'none'; script-src 'nonce-first-script'; style-src 'nonce-first-style'"
      }, body: html}));
      await page.goto(url);
      await expect.poll(() => page.evaluate(() => [globalThis.__classic, globalThis.__module])).toEqual([1, 1]);
      expect(await page.evaluate(() => globalThis.__bootstrapValue)).toBe('</script><script>globalThis.__injected=1</script>');
      expect(await page.evaluate(() => globalThis.__injected)).toBeUndefined();
      await expect(page.locator('h1')).toHaveCSS('color', 'rgb(12, 34, 56)');
      expect(await page.locator('script').evaluateAll(nodes => nodes.map(node => node.nonce))).toEqual(['first-script', 'first-script', 'first-script']);
      // Pinned React serializes an object nonce as [object Object] on the classic
      // preload link (the actual script nonce is correct). Keep this upstream
      // defect visible and reject every other console/page error.
      expect(html).toContain('nonce="[object Object]" href="/bootstrap-classic.js"');
      expect(errors).toHaveLength(1);
      expect(errors[0]).toContain("Loading the script 'http://127.0.0.1:");
      expect(errors[0]).toContain("/bootstrap-classic.js' violates the following Content Security Policy directive");
    }
  }
  // A different request's policy must deny all three scripts and the managed style.
  errors.length = 0;
  await page.addInitScript(() => {
    globalThis.__violations = [];
    addEventListener('securitypolicyviolation', event => globalThis.__violations.push(event.effectiveDirective));
  });
  const denied = new URL('/bootstrap-denied', info.project.use.baseURL).href;
  const html = await server.renderBootstrap('pipeable', 'split', 'first');
  await page.route(denied, route => route.fulfill({contentType: 'text/html', headers: {
    'content-security-policy': "default-src 'none'; script-src 'nonce-second-script'; style-src 'nonce-second-style'"
  }, body: html}));
  await page.goto(denied);
  await expect.poll(() => page.evaluate(() => globalThis.__violations)).toEqual(expect.arrayContaining(['script-src-elem', 'style-src-elem']));
  expect(await page.evaluate(() => [globalThis.__bootstrapValue, globalThis.__classic, globalThis.__module, globalThis.__injected]))
    .toEqual([undefined, undefined, undefined, undefined]);
  await expect(page.locator('h1')).toHaveCSS('color', 'rgb(0, 0, 0)');
  expect(errors.length).toBeGreaterThan(0);
  expect(errors.every(message => message.includes('Content Security Policy'))).toBe(true);
});
