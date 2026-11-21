import {referenceOnly, expect, log} from './bench.js';

// React features that JX does not offer yet. Each test passes on the reference
// and reports `unsupported` on JX until the matching parity task lands.

referenceOnly('insertion-effect: insertion effects run before layout and passive effects', async ({page, open}) => {
  await open('insertion-effect');
  await expect.poll(async () => (await log(page)).slice(0, 3)).toEqual(['insertion', 'layout', 'effect']);
});

referenceOnly('effect-event: an effect event reads the latest values without re-running the effect', async ({page, open}) => {
  await open('effect-event');
  const mount = ['connected a light', 'disconnected a', 'connected a light'];
  await expect.poll(() => log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Change theme'}).click();
  await expect(page.getByRole('status')).toHaveText('a dark');
  expect(await log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Change room'}).click();
  await expect.poll(() => log(page)).toEqual([...mount, 'disconnected a', 'connected b dark']);
});

referenceOnly('transition-pending: useTransition reports pending while an async action runs', async ({page, open}) => {
  await open('transition-pending');
  await expect(page.getByRole('status')).toHaveText('idle');
  await page.getByRole('button', {name: 'Start'}).click();
  await expect(page.getByRole('status')).toHaveText('pending');
  await page.getByRole('button', {name: 'Finish'}).click();
  await expect(page.getByRole('status')).toHaveText('done');
});

referenceOnly('start-transition: a standalone transition update is applied with the urgent one', async ({page, open}) => {
  await open('start-transition');
  await page.getByRole('button', {name: 'Switch tab'}).click();
  await expect(page.getByRole('status')).toHaveText('second 1');
});

referenceOnly('deferred-value: the initial value renders first, then the real value', async ({page, open}) => {
  await open('deferred-value');
  await expect(page.getByRole('status')).toHaveText('ready');
  const first = await log(page);
  expect(first[0]).toEqual('initial');
  expect(first.at(-1)).toEqual('ready');
  await page.getByRole('button', {name: 'Change'}).click();
  await expect(page.getByRole('status')).toHaveText('changed');
});

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

referenceOnly('use-promise: use suspends on a promise, reads context, and may be conditional', async ({page, open}) => {
  await open('use-promise');
  await expect(page.getByRole('status')).toHaveText('loading');
  await page.getByRole('button', {name: 'Resolve'}).click();
  await expect(page.getByRole('status')).toHaveText('loaded bright');
});

referenceOnly('lazy-component: a lazy component shows the Suspense fallback until it loads', async ({page, open}) => {
  await open('lazy-component');
  await expect(page.getByRole('status')).toHaveText('loading');
  await page.getByRole('button', {name: 'Load'}).click();
  await expect(page.getByRole('status')).toHaveText('late content');
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

referenceOnly('profiler: onRender reports the mount and each update', async ({page, open}) => {
  await open('profiler');
  await expect.poll(async () => (await log(page))[0]).toEqual('bench mount');
  await page.getByRole('button', {name: 'Update'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  await expect.poll(async () => (await log(page)).at(-1)).toEqual('bench update');
});

referenceOnly('view-transition: a typed transition updates content inside a ViewTransition', async ({page, open}) => {
  await open('view-transition');
  await expect(page.getByRole('status')).toHaveText('first');
  await page.getByRole('button', {name: 'Go forward'}).click();
  await expect(page.getByRole('status')).toHaveText('second');
});

referenceOnly('clone-element: a cloned element merges new props and children', async ({page, open}) => {
  await open('clone-element');
  const button = page.getByRole('button');
  await expect(button).toHaveText('Cloned');
  await expect(button).toHaveClass('cloned');
  await expect(button).toHaveAttribute('title', 'original');
  await expect(button).toHaveAttribute('data-extra', 'yes');
});

referenceOnly('children-utilities: Children counts, flattens and maps the children it is given', async ({page, open}) => {
  await open('children-utilities');
  await expect(page.getByRole('status')).toHaveText('4/3');
  // map visits the empty child too; toArray drops it.
  expect(await page.locator('li').allTextContents()).toEqual(['a', '', 'b', 'c']);
});

referenceOnly('portal: portal content renders elsewhere in the DOM but events reach the React parent', async ({page, open}) => {
  await open('portal');
  const ported = page.getByTestId('ported');
  expect(await ported.evaluate(node => node.parentElement === document.body)).toBe(true);
  expect(await page.getByTestId('owner').evaluate(node => node.querySelector('[data-testid="ported"]'))).toBeNull();
  await ported.click();
  await expect(page.getByRole('status')).toHaveText('1');
});

referenceOnly('flush-sync: the DOM is updated before flushSync returns', async ({page, open}) => {
  await open('flush-sync');
  await page.getByRole('button', {name: 'Flush'}).click();
  await expect(page.getByTestId('seen')).toHaveText('dom showed 1');
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

referenceOnly('spread-props: spread attributes and props follow source order', async ({page, open}) => {
  await open('spread-props');
  const button = page.getByRole('button');
  await expect(button).toHaveAttribute('data-kind', 'spread');
  await expect(button).toHaveAttribute('title', 'explicit wins');
  await expect(page.getByText('Earlier loses')).toHaveAttribute('title', 'from spread');
  await expect(page.getByTestId('label')).toHaveText('spread text');
  await expect(page.getByTestId('label')).toHaveAttribute('data-tone', 'calm');
});

referenceOnly('default-props: an omitted prop takes its default and an absent one adds no attribute', async ({page, open}) => {
  await open('default-props');
  const fallback = page.getByTestId('badge-default-label');
  await expect(fallback).toHaveText('default label');
  expect(await fallback.evaluate(node => node.hasAttribute('data-tone'))).toBe(false);
  await expect(page.getByTestId('badge-given')).toHaveAttribute('data-tone', 'warm');
});

referenceOnly('generic-list: one component renders lists of different item types', async ({page, open}) => {
  await open('generic-list');
  expect(await page.locator('li').allTextContents()).toEqual(['X', 'Y', '1:one', '2:two']);
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

referenceOnly('debug-value: a hook labelled with useDebugValue behaves like the plain hook', async ({page, open}) => {
  await open('debug-value');
  await page.getByRole('button', {name: 'Increment'}).click();
  await expect(page.getByRole('status')).toHaveText('2');
});
