import {test, expect, log} from './bench.js';

test('class-lifecycle-authoring: direct class markup composes context, static partial derivation, Pure and committed error recovery', async ({page, open}) => {
  const problems = await open('class-lifecycle-authoring', {expectErrors: true});
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const value = page.getByTestId('authored-lifecycle-value');
  const child = page.getByTestId('authored-lifecycle-child');
  await expect(value).toHaveText('day:0:ready');
  await expect(child).toHaveText('Child: 0');
  expect(problems).toEqual([]);
  const boundary = await page.getByTestId('authored-lifecycle-boundary').elementHandle();
  const originalChild = await child.elementHandle();
  try {
    await click('Increment lifecycle boundary');
    await expect(value).toHaveText('day:1:ready');
    await child.click();
    await expect(child).toHaveText('Child: 1');
    let offset = (await log(page)).length;
    await click('Equal lifecycle props');
    await expect(page.getByTestId('authored-lifecycle-pulse')).toHaveText('1');
    expect((await log(page)).slice(offset)).not.toContain('authored-lifecycle:render');
    await click('Change lifecycle context');
    await expect(value).toHaveText('night:1:ready');
    await expect(child).toHaveText('Child: 1');
    expect(await child.evaluate((node, original) => node === original, originalChild)).toBe(true);
    await click('Advance lifecycle revision');
    await expect(value).toHaveText('night:10:<null>');
    await expect(child).toHaveText('Child: 1');
    expect(await page.getByTestId('authored-lifecycle-boundary').evaluate((node, original) => node === original, boundary)).toBe(true);
    await click('Increment lifecycle boundary');
    await expect(value).toHaveText('night:11:<null>');
    offset = (await log(page)).length;
    await click('Fail authored child');
    await expect(page.getByTestId('authored-lifecycle-fallback')).toHaveText('night:11:<null>');
    await expect(child).toHaveCount(0);
    await expect.poll(async () => (await log(page)).slice(offset))
      .toContain('authored-lifecycle:caught:same=true:original=true:committed=true:stack=true');
    expect(await originalChild.evaluate(node => node.isConnected)).toBe(false);
    expect(await boundary.evaluate(node => node.isConnected)).toBe(false);
    await click('Null lifecycle context');
    await expect(page.getByTestId('authored-lifecycle-fallback')).toHaveText('<null>:11:<null>');
    await click('Recover authored child');
    await expect(child).toHaveText('Child: 0');
    await expect(value).toHaveText('<null>:11:<null>');
    expect(await child.evaluate((node, original) => node === original, originalChild)).toBe(false);
    await click('Inspect lifecycle identity');
    await expect(page.getByTestId('authored-lifecycle-identity')).toHaveText('same');
    await click('Increment lifecycle boundary');
    await expect(value).toHaveText('<null>:12:<null>');
  } finally {
    await originalChild.dispose();
    await boundary.dispose();
  }
});
