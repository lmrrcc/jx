import {test, expect, log} from './bench.js';

test('class-error-lifecycle: native class boundaries recover child render, derive and commit failures with typed identity and committed reports', async ({page, open}) => {
  const problems = await open('class-error-lifecycle', {expectErrors: true});
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const leaf = page.getByTestId('failure-leaf');
  const innerCount = page.getByTestId('inner-count');
  const outerCount = page.getByTestId('outer-count');
  const sibling = page.getByTestId('error-sibling');
  const mark = async () => (await log(page)).length;
  const since = async offset => (await log(page)).slice(offset);
  await expect(leaf).toBeVisible();
  await expect(innerCount).toHaveText('0');
  await expect(outerCount).toHaveText('0');
  expect(problems).toEqual([]);
  const siblingNode = await sibling.elementHandle();
  await click('Increment boundary');
  await expect(innerCount).toHaveText('1');
  try {
    for (const [button, kind] of [
      ['Fail render', 'java'], ['Fail derive', 'java'], ['Fail commit', 'java'],
      ['Fail native error', 'native-error:OBJECT'],
      ['Fail native object', 'native-object:OBJECT'],
      ['Fail native null', 'native-null:NULL'],
      ['Fail native undefined', 'native-undefined:UNDEFINED']
    ]) {
      await click('Increment leaf');
      await expect(page.getByTestId('failure-leaf-count')).toHaveText('1');
      const previousLeaf = await leaf.elementHandle();
      const offset = await mark();
      await click(button);
      await expect(page.getByTestId('inner-fallback')).toHaveText('inner:fallback:1');
      await expect(leaf).toHaveCount(0);
      await expect.poll(() => since(offset)).toContain('inner:caught:' + kind + ':same=true:committed=true:stack=true');
      expect((await since(offset)).filter(entry => entry.startsWith('outer:caught:'))).toEqual([]);
      expect(await previousLeaf.evaluate(node => node.isConnected)).toBe(false);
      await expect(outerCount).toHaveText('0');
      await click('Reset errors');
      await expect(leaf).toBeVisible();
      await expect(page.getByTestId('failure-leaf-count')).toHaveText('0');
      await expect(innerCount).toHaveText('1');
      expect(await leaf.evaluate((node, before) => node === before, previousLeaf)).toBe(false);
      await previousLeaf.dispose();
    }
    let offset = await mark();
    await click('Fail own boundary');
    await expect(page.getByTestId('outer-fallback')).toHaveText('outer:fallback:0');
    await expect(page.getByTestId('inner-boundary')).toHaveCount(0);
    await expect.poll(() => since(offset)).toContain('outer:caught:java:same=true:committed=true:stack=true');
    expect((await since(offset)).filter(entry => entry.startsWith('inner:caught:'))).toEqual([]);
    await click('Reset errors');
    await expect(leaf).toBeVisible();
    await expect(innerCount).toHaveText('0');
    await expect(outerCount).toHaveText('0');

    offset = await mark();
    const beforeEvent = problems.length;
    const eventLeaf = await leaf.elementHandle();
    await click('Throw event');
    await expect.poll(() => problems.slice(beforeEvent).some(problem => problem.startsWith('pageerror:') && problem.includes('class child failed'))).toBe(true);
    await expect(leaf).toBeVisible();
    await expect(page.getByTestId('inner-fallback')).toHaveCount(0);
    await expect(page.getByTestId('outer-fallback')).toHaveCount(0);
    expect((await since(offset)).filter(entry => entry.includes(':caught:'))).toEqual([]);
    expect(await leaf.evaluate((node, before) => node === before, eventLeaf)).toBe(true);
    await eventLeaf.dispose();

    await click('Increment boundary');
    await expect(innerCount).toHaveText('1');
    const boundaryNode = await page.getByTestId('outer-boundary').elementHandle();
    await click('Remount boundaries');
    await expect(innerCount).toHaveText('0');
    expect(await boundaryNode.evaluate(node => node.isConnected)).toBe(false);
    await boundaryNode.dispose();
    expect(await sibling.evaluate((node, before) => node === before, siblingNode)).toBe(true);
    const uncaught = problems.filter(problem => problem.startsWith('pageerror:'));
    expect(uncaught).toHaveLength(1);
    expect(uncaught[0]).toContain('class child failed');
    const reported = problems.filter(problem => problem.startsWith('console:'));
    expect(reported).toEqual([
      expect.stringContaining('class child failed'),
      expect.stringContaining('class child failed'),
      expect.stringContaining('class child failed'),
      expect.stringContaining('foreign render'),
      expect.stringContaining('foreign object'),
      expect.stringContaining(' null The above error occurred in the <NativeFailure> component.'),
      expect.stringContaining(' undefined The above error occurred in the <NativeFailure> component.'),
      expect.stringContaining('class child failed')
    ]);
    for (const problem of reported) {
      expect(problem).toContain('The above error occurred in');
      expect(problem).toContain('using the error boundary you provided, FailureBoundary.');
    }
  } finally {
    await siblingNode.dispose();
  }
});
