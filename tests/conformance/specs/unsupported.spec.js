import {referenceOnly, expect, log} from './bench.js';

// React features that JX does not offer yet. Each test passes on the reference
// and reports `unsupported` on JX until the matching parity task lands.

referenceOnly('hydrate-root: hydration attaches to server markup without replacing its nodes', async ({page, open}) => {
  await open('hydrate-root');
  const button = page.getByRole('button', {name: 'Increment'});
  await button.click();
  await expect(page.getByRole('status')).toHaveText('1');
  // The marker was put on the server-rendered node before hydration.
  expect(await button.evaluate(node => node.benchServerNode)).toBe(true);
});

