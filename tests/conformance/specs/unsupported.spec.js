import {referenceOnly, expect, log} from './bench.js';

// React features that JX does not offer yet. Each test passes on the reference
// and reports `unsupported` on JX until the matching parity task lands.

referenceOnly('optimistic: the optimistic value shows during the action, then the confirmed one', async ({page, open}) => {
  await open('optimistic');
  await expect(page.getByRole('status')).toHaveText('saved');
  await page.getByRole('button', {name: 'Send'}).click();
  await expect(page.getByRole('status')).toHaveText('sending');
  await page.getByRole('button', {name: 'Settle'}).click();
  await expect(page.getByRole('status')).toHaveText('confirmed');
});

referenceOnly('action-state: a form action receives the previous state and the form data', async ({page, open}) => {
  await open('action-state');
  await expect(page.getByRole('status')).toHaveText('0');
  await page.getByRole('button', {name: 'Add'}).click();
  await expect(page.getByRole('status')).toHaveText('2');
  await page.getByLabel('Step').fill('5');
  await page.getByRole('button', {name: 'Add'}).click();
  await expect(page.getByRole('status')).toHaveText('7');
});

referenceOnly('form-status: a child of the form sees the pending submission', async ({page, open}) => {
  await open('form-status');
  await page.getByRole('button', {name: 'Send'}).click();
  await expect(page.getByRole('button', {name: 'Sending'})).toBeDisabled();
  await page.getByRole('button', {name: 'Release'}).click();
  await expect(page.getByRole('button', {name: 'Send', exact: true})).toBeEnabled();
});

referenceOnly('form-actions: function actions on a form and on a submit button receive form data', async ({page, open}) => {
  await open('form-actions');
  await page.getByRole('button', {name: 'Save', exact: true}).click();
  await expect(page.getByRole('status')).toHaveText('form Ada');
  await page.getByLabel('Name').fill('Grace');
  await page.getByRole('button', {name: 'Save differently'}).click();
  await expect(page.getByRole('status')).toHaveText('alternative Grace');
});

referenceOnly('form-reset: requestFormReset restores uncontrolled fields to their defaults', async ({page, open}) => {
  await open('form-reset');
  await page.getByLabel('Note').fill('edited');
  await page.getByRole('button', {name: 'Reset form'}).click();
  await expect(page.getByLabel('Note')).toHaveValue('start');
});

referenceOnly('activity: a hidden activity keeps state, hides its DOM and stops its effects', async ({page, open}) => {
  await open('activity');
  await page.getByRole('button', {name: 'Increment'}).click();
  await expect(page.getByTestId('count')).toHaveText('1');
  const before = await log(page);
  expect(before.at(-1)).toEqual('effect on');
  await page.getByRole('button', {name: 'Toggle activity'}).click();
  await expect(page.getByTestId('count')).toBeHidden();
  await expect.poll(async () => (await log(page)).at(-1)).toEqual('effect off');
  await page.getByRole('button', {name: 'Toggle activity'}).click();
  await expect(page.getByTestId('count')).toBeVisible();
  await expect(page.getByTestId('count')).toHaveText('1');
  await expect.poll(async () => (await log(page)).at(-1)).toEqual('effect on');
});

referenceOnly('view-transition: a typed transition updates content inside a ViewTransition', async ({page, open}) => {
  await open('view-transition');
  await expect(page.getByRole('status')).toHaveText('first');
  await page.getByRole('button', {name: 'Go forward'}).click();
  await expect(page.getByRole('status')).toHaveText('second');
});

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

