import {createHash} from 'node:crypto';
import {test, expect} from '@playwright/test';
import {modeOf, sideOf, serverOf} from './bench.js';

test('server-resume-nonce: fresh request nonces authorize resumed instructions and styles without changing static bootstrap', async ({page}, info) => {
  test.skip(modeOf(info) !== 'client', 'complete prerender/resume output is served with its own CSP and no bench-root hydration or lazy route');
  const server = await serverOf(info);
  const reference = await serverOf({project: {name: 'reference'}});
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  for (const kind of ['readable', 'pipeable']) {
    errors.length = 0;
    const [first, second] = await Promise.all([server.resumeBootstrap(kind, 'first'), server.resumeBootstrap(kind, 'second')]);
    if (sideOf(info) === 'jx') expect(first).toEqual(await reference.resumeBootstrap(kind, 'first'));
    expect(first.shell).toBe(second.shell);
    expect(first.shell).toContain('waiting');
    expect(first.shell).not.toContain('nonce=');
    expect(first.rest).toContain('nonce="first-script"');
    expect(first.rest).toContain('nonce="first-style"');
    expect(first.rest).not.toContain('second-');
    expect(second.rest).not.toContain('first-');
    expect(first.rest).not.toContain('resume must not replace');
    expect(first.rest).not.toContain('<h1>shell</h1>');
    // Authorize only these two known static fixture scripts. Hashing arbitrary
    // untrusted HTML would grant authority to injected scripts and is not a host policy.
    const staticScripts = [...first.shell.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map(match => match[1]);
    expect(staticScripts).toEqual([
      'requestAnimationFrame(function(){$RT=performance.now()});',
      'globalThis.__resumeBoot = (globalThis.__resumeBoot || 0) + 1;'
    ]);
    const hashes = staticScripts.map(source => "'sha256-" + createHash('sha256').update(source).digest('base64') + "'").join(' ');
    const url = new URL('/resume-nonce-' + kind, info.project.use.baseURL).href;
    await page.route(url, route => route.fulfill({contentType: 'text/html', headers: {
      'content-security-policy': "default-src 'none'; script-src 'nonce-first-script' " + hashes + "; style-src 'nonce-first-style'"
    }, body: first.shell + first.rest}));
    await page.goto(url);
    await expect(page.locator('p.resume')).toBeVisible();
    await expect(page.locator('p.resume')).toHaveText('arrived');
    await expect(page.locator('p.resume')).toHaveCSS('color', 'rgb(12, 34, 56)');
    await expect(page.getByText('waiting', {exact: true})).toHaveCount(0);
    expect(await page.evaluate(() => globalThis.__resumeBoot)).toBe(1);
    expect(errors).toEqual([]);
    const denied = url + '-denied';
    await page.route(denied, route => route.fulfill({contentType: 'text/html', headers: {
      'content-security-policy': "default-src 'none'; script-src 'nonce-second-script' " + hashes + "; style-src 'nonce-second-style'"
    }, body: first.shell + first.rest}));
    await page.goto(denied);
    await expect(page.getByText('waiting', {exact: true})).toBeVisible();
    await expect(page.locator('p.resume')).toBeHidden();
    expect(await page.evaluate(() => globalThis.__resumeBoot)).toBe(1);
    expect(errors).toHaveLength(2);
    expect(errors.every(message => message.includes('Content Security Policy'))).toBe(true);
  }
});
