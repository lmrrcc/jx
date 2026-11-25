import {test, expect, log} from './bench.js';

test('class-null-patch: null updates retain state identity and skip render while callbacks preserve queue ordering', async ({page, open}) => {
  await open('class-null-patch');
  const value = page.getByTestId('null-patch-value');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const since = async offset => (await log(page)).slice(offset);
  const saw = async (offset, entry) => expect.poll(() => since(offset)).toContain(entry);
  await expect(value).toHaveText('0:ready');
  const node = await value.elementHandle();
  try {
    for (const [button, operation] of [['Direct null patch', 'direct'], ['Functional null patch', 'functional']]) {
      const offset = (await log(page)).length;
      await click(button);
      const callback = 'null-patch:callback:' + operation + ':0:ready:same=true:dom=0:ready';
      await saw(offset, callback);
      const entries = await since(offset);
      expect(entries).toContain('null-patch:handler:' + operation + ':0:ready:same=true');
      expect(entries.filter(entry => /null-patch:(render|should|commit)/.test(entry))).toEqual([]);
      expect(entries.filter(entry => entry.startsWith('null-patch:callback:'))).toEqual([callback]);
      expect(entries.indexOf('null-patch:handler:' + operation + ':0:ready:same=true')).toBeLessThan(entries.indexOf(callback));
      if (operation === 'functional') expect(entries).toContain('null-patch:updater:0');
      await expect(value).toHaveText('0:ready');
    }
    let offset = (await log(page)).length;
    await click('Empty state patch');
    await saw(offset, 'null-patch:callback:empty:0:ready:same=false:dom=0:ready');
    expect(await since(offset)).toContain('null-patch:should:0->0');
    expect(await since(offset)).toContain('null-patch:render');
    expect((await since(offset)).filter(entry => entry.startsWith('null-patch:commit:'))).toEqual(['null-patch:commit:0->0']);

    offset = (await log(page)).length;
    await click('Mixed null queue');
    await saw(offset, 'null-patch:callback:last:1:ready:same=false:dom=1:ready');
    const entries = await since(offset);
    expect(entries.filter(entry => entry.startsWith('null-patch:callback:'))).toEqual([
      'null-patch:callback:first:1:ready:same=false:dom=1:ready',
      'null-patch:callback:middle:1:ready:same=false:dom=1:ready',
      'null-patch:callback:last:1:ready:same=false:dom=1:ready'
    ]);
    expect(entries).toContain('null-patch:handler:mixed:0:ready');
    expect(entries.filter(entry => entry.startsWith('null-patch:commit:'))).toEqual(['null-patch:commit:0->1']);
    expect(entries.indexOf('null-patch:commit:0->1')).toBeLessThan(entries.indexOf('null-patch:callback:first:1:ready:same=false:dom=1:ready'));
    await expect(value).toHaveText('1:ready');
    offset = (await log(page)).length;
    await click('Clear nullable field');
    await saw(offset, 'null-patch:callback:field:1:<null>:same=false:dom=1:<null>');
    await expect(value).toHaveText('1:<null>');
    expect(await value.evaluate((current, original) => current === original, node)).toBe(true);
  } finally { await node.dispose(); }
});
