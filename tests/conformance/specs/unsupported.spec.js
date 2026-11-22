import {referenceOnly, expect, log} from './bench.js';

// React features that JX does not offer yet. Each test passes on the reference
// and reports `unsupported` on JX until the matching parity task lands.

referenceOnly('resource-hints: the six resource functions add their elements to the head', async ({page, open}) => {
  await open('resource-hints');
  const head = selector => page.locator('head ' + selector);
  await expect(head('link[rel="preconnect"][href="https://connect.example.test"]')).toHaveCount(1);
  await expect(head('link[rel="dns-prefetch"][href="https://dns.example.test"]')).toHaveCount(1);
  await expect(head('link[rel="preload"][as="style"]')).toHaveCount(1);
  await expect(head('link[rel="modulepreload"]')).toHaveCount(1);
  await expect(head('script[async][src^="data:text/javascript,globalThis.__benchPreinit=1"]')).toHaveCount(1);
  await expect(head('script[type="module"][src^="data:text/javascript,globalThis.__benchPreinitModule"]')).toHaveCount(1);
  await expect.poll(() => page.evaluate(() => [globalThis.__benchPreinit, globalThis.__benchPreinitModule])).toEqual([1, 1]);
});

referenceOnly('raw-html: explicitly supplied HTML is inserted as markup', async ({page, open}) => {
  await open('raw-html');
  const raw = page.getByTestId('raw');
  expect(await raw.evaluate(node => [...node.children].map(child => child.tagName))).toEqual(['B', 'I']);
  await expect(raw).toHaveText('bold and italic');
});

referenceOnly('hydrate-root: hydration attaches to server markup without replacing its nodes', async ({page, open}) => {
  await open('hydrate-root');
  const button = page.getByRole('button', {name: 'Increment'});
  await button.click();
  await expect(page.getByRole('status')).toHaveText('1');
  // The marker was put on the server-rendered node before hydration.
  expect(await button.evaluate(node => node.benchServerNode)).toBe(true);
});

