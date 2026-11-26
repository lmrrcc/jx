import {test, expect, chromium} from '@playwright/test';
import {mkdtemp, readFile, cp, writeFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';

const artifact = path.resolve('examples/mv3-app/target/mv3-extracted');
let context, protocol, profile;

// Playwright does not expose Chrome action popups as Page objects. This small
// test-only CDP session observes their actual target; it never navigates a tab
// to the popup URL or substitutes a synthetic DOM for the application.
async function popupSession(targetId) {
  const {sessionId} = await protocol.send('Target.attachToTarget', {targetId, flatten: false});
  let sequence = 0;
  const send = async (method, params = {}) => {
    const id = ++sequence;
    let listener, timer;
    try {
      return await new Promise((resolve, reject) => {
        listener = event => {
          if (event.sessionId !== sessionId) return;
          const message = JSON.parse(event.message);
          if (message.id !== id) return;
          if (message.error) reject(new Error(message.error.message)); else resolve(message.result);
        };
        protocol.on('Target.receivedMessageFromTarget', listener);
        timer = setTimeout(() => reject(new Error(`Popup command timed out: ${method}`)), 5000);
        protocol.send('Target.sendMessageToTarget', {sessionId, message: JSON.stringify({id, method, params})}).catch(reject);
      });
    } finally {
      clearTimeout(timer);
      protocol.off('Target.receivedMessageFromTarget', listener);
    }
  };
  return async expression => {
    const result = await send('Runtime.evaluate', {expression, returnByValue: true, allowUnsafeEvalBlockedByCSP: false});
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text);
    return result.result.value;
  };
}

test.beforeEach(async () => {
  profile = await mkdtemp(path.join(tmpdir(), 'jx-mv3-profile-'));
  context = await chromium.launchPersistentContext(profile, {
    channel: 'chrome', headless: true,
    args: ['--enable-unsafe-extension-debugging'],
    ignoreDefaultArgs: ['--disable-extensions']
  });
  protocol = await context.browser().newBrowserCDPSession();
  for (const page of context.pages()) await page.close();
});
test.afterEach(async () => {
  await context?.close();
  if (profile) await rm(profile, {recursive: true, force: true});
});

test('Chrome action opens the declared popup as a real extension target', async () => {
  const {id} = await protocol.send('Extensions.loadUnpacked', {path: artifact});
  const tab = await context.newPage();
  const {targetInfos: tabs} = await protocol.send('Target.getTargets', {filter: [{type: 'tab'}, {exclude: true}]});
  expect(tabs).toHaveLength(1);
  const openPopup = async () => {
    await protocol.send('Extensions.triggerAction', {id, targetId: tabs[0].targetId});
    const targets = async () => (await protocol.send('Target.getTargets')).targetInfos
      .filter(target => target.url === `chrome-extension://${id}/index.html`);
    await expect.poll(async () => (await targets()).length).toBe(1);
    const target = (await targets())[0];
    const evaluate = await popupSession(target.targetId);
    await expect.poll(() => evaluate("document.querySelector('#count')?.textContent")).toBe('0');
    return {targetId: target.targetId, evaluate};
  };
  let popup = await openPopup();
  await popup.evaluate("document.querySelector('#round-trip').click()");
  await expect.poll(() => popup.evaluate("document.querySelector('#count').textContent")).toBe('1');
  expect(await popup.evaluate("document.querySelector('#result').textContent")).toBe('Round trip verified');
  const firstTarget = popup.targetId;
  expect((await protocol.send('Target.closeTarget', {targetId: firstTarget})).success).toBe(true);
  await expect.poll(async () => (await protocol.send('Target.getTargets')).targetInfos.some(target => target.targetId === firstTarget)).toBe(false);
  popup = await openPopup();
  expect(popup.targetId).not.toBe(firstTarget);
  expect(await popup.evaluate("document.querySelector('#result').textContent")).toBe('Ready');
  await popup.evaluate("document.querySelector('#round-trip').click()");
  await expect.poll(() => popup.evaluate("document.querySelector('#count').textContent")).toBe('1');
  expect(await popup.evaluate("document.querySelector('#result').textContent")).toBe('Round trip verified');
});

test('the packaged MV3 popup renders Java, calls Rust WASM under CSP and starts fresh after close', async () => {
  const {id} = await protocol.send('Extensions.loadUnpacked', {path: artifact});
  const manifest = JSON.parse(await readFile(path.join(artifact, 'manifest.json'), 'utf8'));
  expect(manifest.manifest_version).toBe(3);
  expect(manifest.permissions).toBeUndefined();
  expect(manifest.host_permissions).toBeUndefined();
  const errors = [], remoteRequests = [], wasmResponses = [];
  const open = async () => {
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
    page.on('request', request => { if (/^https?:/.test(request.url())) remoteRequests.push(request.url()); });
    page.on('response', response => { if (response.url().endsWith('.wasm')) wasmResponses.push(response.status()); });
    await page.goto(`chrome-extension://${id}/${manifest.action.default_popup}`);
    await expect(page.locator('h1')).toHaveText('JX extension');
    await expect(page.locator('#count')).toHaveText('0');
    return page;
  };
  let page = await open();
  await page.locator('#round-trip').click();
  await page.locator('#round-trip').click();
  await expect(page.locator('#count')).toHaveText('2');
  await expect(page.locator('#result')).toHaveText('Round trip verified');
  await expect(page.locator('#decoded')).toHaveText('Java → Rust/WASM ✓ '.repeat(64));
  const bytes = Number(await page.locator('#input-bytes').textContent());
  expect(bytes).toBe(new TextEncoder().encode('Java → Rust/WASM ✓ '.repeat(64)).length);
  expect(Number(await page.locator('#compressed-bytes').textContent())).toBeLessThan(bytes);
  await page.close();
  page = await open();
  await expect(page.locator('#result')).toHaveText('Ready');
  await page.locator('#round-trip').click();
  await expect(page.locator('#count')).toHaveText('1');
  await expect(page.locator('#result')).toHaveText('Round trip verified');
  expect(wasmResponses).toEqual([200, 200]);
  expect(remoteRequests).toEqual([]);
  expect(errors).toEqual([]);
});

test('MV3 rejects WASM compilation when the explicit CSP permission is removed', async () => {
  const restricted = await mkdtemp(path.join(tmpdir(), 'jx-mv3-restricted-'));
  try {
    await cp(artifact, restricted, {recursive: true});
    const manifestPath = path.join(restricted, 'manifest.json');
    const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
    manifest.content_security_policy.extension_pages = manifest.content_security_policy.extension_pages.replace(" 'wasm-unsafe-eval'", '');
    await writeFile(manifestPath, JSON.stringify(manifest));
    const {id} = await protocol.send('Extensions.loadUnpacked', {path: restricted});
    const page = await context.newPage();
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    await page.goto(`chrome-extension://${id}/index.html`);
    await expect(page.locator('#startup')).toHaveText('WASM initialization failed');
    await expect(page.locator('#round-trip')).toHaveCount(0);
    await expect.poll(() => errors.join('\n')).toMatch(/Content Security Policy|unsafe-eval|wasm-unsafe-eval/i);
  } finally {
    await rm(restricted, {recursive: true, force: true});
  }
});
