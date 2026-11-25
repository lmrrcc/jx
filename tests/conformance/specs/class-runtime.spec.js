import {test, expect, log} from './bench.js';

test('class-runtime: native class queues, snapshots, update policy, Pure comparisons and instance refs retain React semantics', async ({page, open}) => {
  await open('class-runtime');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const normal = page.getByTestId('normal-value');
  const pure = page.getByTestId('pure-value');
  const mark = async () => (await log(page)).length;
  const since = async offset => (await log(page)).slice(offset);
  const saw = async (offset, entry) => expect.poll(() => since(offset)).toContain(entry);
  const commits = (entries, prefix) => entries.filter(entry => entry.startsWith(prefix + ':commit:'));
  const callbacks = (entries, prefix) => entries.filter(entry => entry.startsWith(prefix + ':callback:'));
  const lifecycle = async prefix => (await log(page)).filter(entry => entry === prefix + ':mount' || entry === prefix + ':unmount');
  const inspect = async (count, note, allow) => {
    await click('Inspect instance');
    await expect(page.getByTestId('instance-read')).toHaveText(`count=${count};note=${note};allow=${allow}`);
  };
  const inspectIdentity = async () => {
    await click('Inspect snapshot identity');
    await expect(page.getByTestId('snapshot-identity')).toHaveText('props=same;state=same');
  };
  const normalUpdate = async (button, before, after, count) => {
    const offset = await mark();
    await click(button);
    const commit = `normal:commit:${before}->${after}`;
    const callback = `normal:callback:${count}:${after}`;
    await saw(offset, callback);
    await expect(normal).toHaveText(after);
    const entries = await since(offset);
    expect(commits(entries, 'normal')).toEqual([commit]);
    expect(callbacks(entries, 'normal')).toEqual([callback]);
    expect(entries.indexOf(commit)).toBeLessThan(entries.indexOf(callback));
    return entries;
  };

  await expect(normal).toHaveText('0:ready');
  await expect(pure).toHaveText('0:ready');
  await inspectIdentity();
  const added = await normalUpdate('Add three', '0:ready', '3:ready', 3);
  expect(added).toContain('normal:handler:0');
  expect(added.indexOf('normal:handler:0')).toBeLessThan(added.indexOf('normal:commit:0:ready->3:ready'));
  // Count-only patches leave note present; explicit null replaces it without resetting count.
  await normalUpdate('Clear note', '3:ready', '3:<null>', 3);
  await normalUpdate('Empty patch', '3:<null>', '3:<null>', 3);

  let offset = await mark();
  await click('Null update');
  await saw(offset, 'normal:callback:3:3:<null>');
  expect((await since(offset)).filter(entry => entry.startsWith('normal:')))
    .toEqual(['normal:callback:3:3:<null>']);
  await expect(normal).toHaveText('3:<null>');

  // PureComponent compares individual state fields, even though each patch creates a new state object.
  for (const button of ['Pure empty patch', 'Pure same state']) {
    offset = await mark();
    await click(button);
    await saw(offset, 'pure:callback:0:0:ready');
    expect((await since(offset)).filter(entry => entry.startsWith('pure:')))
      .toEqual(['pure:callback:0:0:ready']);
  }
  offset = await mark();
  await click('Pure add one');
  await saw(offset, 'pure:callback:1:1:ready');
  await expect(pure).toHaveText('1:ready');
  let entries = await since(offset);
  expect(commits(entries, 'pure')).toEqual(['pure:commit:0:ready->1:ready']);
  expect(callbacks(entries, 'pure')).toEqual(['pure:callback:1:1:ready']);
  expect(entries.indexOf('pure:commit:0:ready->1:ready')).toBeLessThan(entries.indexOf('pure:callback:1:1:ready'));

  const parentUpdate = async (button, pureRenders) => {
    const offset = await mark();
    await click(button);
    // Normal's commit proves a new parent props object actually reached both peers.
    await saw(offset, 'normal:commit:3:<null>->3:<null>');
    if (pureRenders) {
      await saw(offset, 'pure:commit:1:ready->1:ready');
      expect(await since(offset)).toContain('pure:render');
    } else {
      expect((await since(offset)).filter(entry => entry.startsWith('pure:'))).toEqual([]);
    }
    await expect(pure).toHaveText('1:ready');
  };
  await parentUpdate('Equal props', false);
  await parentUpdate('New nested value', true);
  await parentUpdate('NaN prop', true);
  await parentUpdate('Equal props', false); // Object.is(NaN, NaN), not Java/JS numeric ==.
  await parentUpdate('Negative zero prop', true);
  await parentUpdate('Positive zero prop', true); // Object.is distinguishes -0 from +0.
  await parentUpdate('Equal props', false);

  offset = await mark();
  await click('Block normal');
  await saw(offset, 'pure:commit:1:ready->1:ready');
  expect((await since(offset)).filter(entry => entry.startsWith('normal:'))).toEqual([]);
  await inspect(3, '<null>', false);
  await inspectIdentity();
  offset = await mark();
  await click('Add three');
  await saw(offset, 'normal:callback:6:3:<null>');
  expect((await since(offset)).filter(entry => entry.startsWith('normal:')))
    .toEqual(['normal:handler:3', 'normal:callback:6:3:<null>']);
  await expect(normal).toHaveText('3:<null>');
  await inspect(6, '<null>', false); // The instance advances even though its DOM did not render.
  await inspectIdentity();

  offset = await mark();
  await click('Force normal');
  await saw(offset, 'normal:commit:3:<null>->6:<null>');
  await expect(normal).toHaveText('6:<null>');
  entries = await since(offset);
  expect(entries).toContain('normal:render');
  expect(commits(entries, 'normal')).toEqual(['normal:commit:3:<null>->6:<null>']);
  expect(callbacks(entries, 'normal')).toEqual([]);
  await inspectIdentity();
  await click('Allow normal');
  await inspect(6, '<null>', true);

  const beforeNullLifecycle = await lifecycle('normal');
  const beforeNullNode = await normal.elementHandle();
  expect(beforeNullNode).not.toBeNull();
  try {
    offset = await mark();
    await click('Hide normal output');
    await expect(normal).toHaveCount(0);
    await saw(offset, 'normal:commit:6:<null>-><none>');
    expect(await beforeNullNode.evaluate(node => node.isConnected)).toBe(false);
    await inspect(6, 'hidden', true);
    await inspectIdentity();
    expect(await lifecycle('normal')).toEqual(beforeNullLifecycle);

    offset = await mark();
    await click('Show normal output');
    await expect(normal).toHaveText('6:ready');
    await saw(offset, 'normal:commit:<none>->6:ready');
    expect(await normal.evaluate((node, previous) => node === previous, beforeNullNode)).toBe(false);
    expect(await lifecycle('normal')).toEqual(beforeNullLifecycle);
  } finally {
    await beforeNullNode.dispose();
  }

  const beforeRemountNode = await normal.elementHandle();
  const pureNode = await pure.elementHandle();
  expect(beforeRemountNode).not.toBeNull();
  expect(pureNode).not.toBeNull();
  try {
    offset = await mark();
    await click('Remount normal');
    await expect(normal).toHaveText('0:ready');
    await saw(offset, 'normal:unmount');
    await saw(offset, 'normal:mount');
    expect(await beforeRemountNode.evaluate(node => node.isConnected)).toBe(false);
    expect(await normal.evaluate((node, previous) => node === previous, beforeRemountNode)).toBe(false);
    expect(await pure.evaluate((node, previous) => node === previous, pureNode)).toBe(true);
    await expect(pure).toHaveText('1:ready');
    expect((await since(offset)).filter(entry => entry.startsWith('pure:'))).toEqual([]);
    await inspect(0, 'ready', true);

    await click('Unmount normal');
    await expect(normal).toHaveCount(0);
    await click('Inspect instance');
    await expect(page.getByTestId('instance-read')).toHaveText('detached');
    await expect(page.getByRole('button', {name: 'Add three', exact: true})).toBeDisabled();
    await expect(page.getByRole('button', {name: 'Inspect snapshot identity', exact: true})).toBeDisabled();
    await expect(pure).toHaveText('1:ready');
    expect(await pure.evaluate((node, previous) => node === previous, pureNode)).toBe(true);
    const normalLifecycle = await lifecycle('normal');
    expect(normalLifecycle.filter(entry => entry === 'normal:mount').length).toBeGreaterThan(0);
    expect(normalLifecycle.filter(entry => entry === 'normal:unmount').length)
      .toBe(normalLifecycle.filter(entry => entry === 'normal:mount').length);
  } finally {
    await beforeRemountNode.dispose();
    await pureNode.dispose();
  }
});
