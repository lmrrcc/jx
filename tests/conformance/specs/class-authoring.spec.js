import {test, expect, log} from './bench.js';

test('class-authoring: authored classes retain native partial updates, lifecycle snapshots, Pure props and checked instance refs', async ({page, open}) => {
  await open('class-authoring');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const normal = page.getByTestId('authored-value');
  const pure = page.getByTestId('authored-pure-value');
  const mark = async () => (await log(page)).length;
  const since = async offset => (await log(page)).slice(offset);
  const saw = async (offset, entry) => expect.poll(() => since(offset)).toContain(entry);
  const inspect = async expected => {
    await click('Inspect authored ref');
    await expect(page.getByTestId('authored-inspection')).toHaveText(expected);
  };
  await expect(normal).toHaveText('Count:2:ready');
  await expect(pure).toHaveText('Pure:0');
  await expect.poll(() => log(page)).toContain('authored:mount');
  await expect.poll(() => log(page)).toContain('authored-pure:mount');
  const originalNormal = await normal.elementHandle();
  const originalPure = await pure.elementHandle();
  expect(originalNormal).not.toBeNull();
  expect(originalPure).not.toBeNull();
  try {
    let offset = await mark();
    await click('Authored add three');
    await saw(offset, 'authored:callback:5');
    await expect(normal).toHaveText('Count:5:ready');
    const added = await since(offset);
    expect(added.filter(entry => entry.startsWith('authored:commit:')))
      .toEqual(['authored:commit:Count:2:ready->Count:5:ready']);
    expect(added.filter(entry => entry.startsWith('authored:callback:'))).toEqual(['authored:callback:5']);
    expect(added).toContain('authored:handler:2');
    expect(added.indexOf('authored:handler:2')).toBeLessThan(added.indexOf('authored:commit:Count:2:ready->Count:5:ready'));
    expect(added.indexOf('authored:commit:Count:2:ready->Count:5:ready')).toBeLessThan(added.indexOf('authored:callback:5'));
    expect(await normal.evaluate((node, original) => node === original, originalNormal)).toBe(true);
    await inspect('count=5');

    offset = await mark();
    await click('Clear authored note');
    await expect(normal).toHaveText('Count:5:<null>');
    await saw(offset, 'authored:commit:Count:5:ready->Count:5:<null>');
    expect((await since(offset)).filter(entry => entry.startsWith('authored:callback:'))).toEqual([]);

    offset = await mark();
    await click('Authored pure add');
    await expect(pure).toHaveText('Pure:1');
    expect(await since(offset)).toContain('authored-pure:render');
    expect((await since(offset)).filter(entry => entry === 'authored-pure:mount' || entry === 'authored-pure:unmount')).toEqual([]);

    offset = await mark();
    await click('Equal authored props');
    await expect(page.getByTestId('authored-pulse')).toHaveText('1');
    // The normal sibling's commit proves that fresh parent props reached this update.
    await saw(offset, 'authored:commit:Count:5:<null>->Count:5:<null>');
    expect((await since(offset)).filter(entry => entry.startsWith('authored-pure:'))).toEqual([]);
    await expect(pure).toHaveText('Pure:1');
    expect(await pure.evaluate((node, original) => node === original, originalPure)).toBe(true);

    offset = await mark();
    await click('Remount authored');
    await expect(normal).toHaveText('Count:2:ready');
    await saw(offset, 'authored:unmount');
    await saw(offset, 'authored:mount');
    expect(await originalNormal.evaluate(node => node.isConnected)).toBe(false);
    expect(await normal.evaluate((node, original) => node === original, originalNormal)).toBe(false);
    expect(await pure.evaluate((node, original) => node === original, originalPure)).toBe(true);
    await expect(pure).toHaveText('Pure:1');
    expect((await since(offset)).filter(entry => entry.startsWith('authored-pure:'))).toEqual([]);
    await inspect('count=2');

    const remounted = await normal.elementHandle();
    expect(remounted).not.toBeNull();
    try {
      await click('Unmount authored');
      await expect(normal).toHaveCount(0);
      expect(await remounted.evaluate(node => node.isConnected)).toBe(false);
      await inspect('detached');
    } finally { await remounted.dispose(); }
    for (const name of ['Clear authored note', 'Remount authored', 'Unmount authored'])
      await expect(page.getByRole('button', {name, exact: true})).toBeDisabled();
    await expect(pure).toHaveText('Pure:1');
    expect(await pure.evaluate((node, original) => node === original, originalPure)).toBe(true);
    const lifecycle = (await log(page)).filter(entry => entry === 'authored:mount' || entry === 'authored:unmount');
    expect(lifecycle.filter(entry => entry === 'authored:mount').length).toBeGreaterThan(0);
    expect(lifecycle.filter(entry => entry === 'authored:unmount').length)
      .toBe(lifecycle.filter(entry => entry === 'authored:mount').length);
  } finally {
    await originalNormal.dispose();
    await originalPure.dispose();
  }
});
