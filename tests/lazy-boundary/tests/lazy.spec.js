import {test, expect} from '@playwright/test';

// Two separately compiled JX applications: the host, and one it loads on demand.
// These tests record what crosses the boundary between them and what does not.

test('the lazy application is not downloaded until its component is shown', async ({page}) => {
  const scripts = [];
  page.on('request', request => { if (request.url().endsWith('.js')) scripts.push(new URL(request.url()).pathname); });
  await page.goto('/');
  await expect(page.getByRole('button', {name: 'Show late part'})).toBeVisible();
  expect(scripts).toContain('/host.js');
  expect(scripts).not.toContain('/late.js');
  await page.getByRole('button', {name: 'Show late part'}).click();
  await expect(page.getByTestId('late-greeting')).toBeVisible();
  expect(scripts).toContain('/late.js');
});

test('a record, a 64-bit number and a callback cross in encoded form; a context does not', async ({page}) => {
  const problems = [];
  page.on('pageerror', error => problems.push(error.message));
  page.on('console', message => { if (message.type() === 'error') problems.push(message.text()); });
  await page.goto('/');
  await page.getByRole('button', {name: 'Show late part'}).click();
  // Record fields, including a long beyond JavaScript's exact integer range.
  await expect(page.getByTestId('late-greeting')).toHaveText('ADA 1 9007199254740993');
  // Updated props reach the loaded component without losing its own state.
  await page.getByRole('button', {name: 'Count in module'}).click();
  await page.getByRole('button', {name: 'Change props'}).click();
  await expect(page.getByTestId('late-greeting')).toHaveText('ADA 2 9007199254740993');
  await expect(page.getByTestId('late-clicks')).toHaveText('1');
  // A callback of the host is called from the loaded application.
  await page.getByRole('button', {name: 'Pick'}).click();
  await expect(page.getByTestId('picked')).toHaveText('picked Ada');
  // The host's provider is not seen: each application has its own context object.
  await expect(page.getByTestId('host-theme')).toHaveText('dark');
  await expect(page.getByTestId('late-theme')).toHaveText('light');
  expect(problems).toEqual([]);
});

test('an error thrown in the lazy application reaches the host boundary with its message, not its type', async ({page}) => {
  await page.goto('/');
  await page.getByRole('button', {name: 'Show late part'}).click();
  await expect(page.getByTestId('late-greeting')).toBeVisible();
  await page.getByRole('button', {name: 'Break late part'}).click();
  // The lazy application threw UnsupportedOperationException. Its exception object belongs to
  // the other Java runtime, so the host sees a stand-in that keeps the message, not the type.
  await expect(page.getByTestId('failure')).toHaveText('IllegalStateException: late module exploded');
});

test('a failed download reaches the boundary, and retry loads the application', async ({page}) => {
  let failures = 0;
  await page.route('**/late.js', route => { failures += 1; return route.abort(); });
  await page.goto('/');
  await page.getByRole('button', {name: 'Show late part'}).click();
  await expect(page.getByRole('alert')).toBeVisible();
  expect(failures).toBe(1);
  // Still failing: the retry fails again and shows the boundary again.
  await page.getByRole('button', {name: 'Try again'}).click();
  await expect.poll(() => failures).toBe(2);
  await expect(page.getByRole('alert')).toBeVisible();
  // The network is back.
  await page.unroute('**/late.js');
  await page.getByRole('button', {name: 'Try again'}).click();
  await expect(page.getByTestId('late-greeting')).toHaveText('ADA 1 9007199254740993');
});
