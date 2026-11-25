import {test, expect, log} from './bench.js';

test('empty-render: returning null preserves the mounted owner until it is removed', async ({page, open}) => {
  await open('empty-render');
  await expect.poll(async () => (await log(page)).at(-1)).toBe('empty mount');
  const mounted = await log(page);
  const following = await page.getByTestId('following').elementHandle();
  await page.getByRole('button', {name: 'Child 0'}).click();
  await expect(page.getByRole('button', {name: 'Child 1'})).toBeVisible();
  await page.getByRole('button', {name: 'Toggle output', exact: true}).click();
  await expect(page.getByRole('button', {name: /^Child /})).toHaveCount(0);
  expect(await log(page)).toEqual(mounted);
  expect(await following.evaluate(node => node === document.querySelector('[data-testid="following"]'))).toBe(true);
  await page.getByRole('button', {name: 'Toggle output', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Child 1'})).toBeVisible();
  expect(await log(page)).toEqual(mounted);
  await page.getByRole('button', {name: 'Toggle output', exact: true}).click();
  await page.getByRole('button', {name: 'Toggle owner', exact: true}).click();
  await expect.poll(() => log(page)).toEqual([...mounted, 'empty unmount']);
  await page.getByRole('button', {name: 'Toggle output', exact: true}).click();
  await page.getByRole('button', {name: 'Toggle owner', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Child 0'})).toBeVisible();
  expect(await following.evaluate(node => node === document.querySelector('[data-testid="following"]'))).toBe(true);
});

test('memo-scalars: default comparison follows native scalar values and opaque object identity', async ({page, open}) => {
  await open('memo-scalars');
  await expect(page.getByTestId('memo-scalar')).toHaveText('present');
  // Each flag says whether actual React must invoke the child for the next value.
  const changes = [false, true, true, false, true, false, true, false, true, false, true, true, false];
  for (const [index, changed] of changes.entries()) {
    const before = await log(page);
    await page.getByRole('button', {name: 'Next scalar'}).click();
    await expect(page.getByRole('status')).toHaveText(String(index + 1));
    if (changed) expect((await log(page)).length).toBeGreaterThan(before.length);
    else expect(await log(page)).toEqual(before);
  }
});
