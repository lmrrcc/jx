import {test, expect, log} from './bench.js';

test('class-derived-props: native static derivation merges after queued updates and before class render policies', async ({page, open}) => {
  await open('class-derived-props');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const row = id => page.getByTestId('derived-' + id);
  const mark = async () => (await log(page)).length;
  const since = async offset => (await log(page)).slice(offset);
  const saw = async (offset, value) => expect.poll(() => since(offset)).toContain(value);
  const shown = async (id, value) => expect(row(id)).toHaveText(id + ':' + value + ':born=1');
  const inspect = async value => {
    await click('Inspect derived state');
    await expect(page.getByTestId('derived-inspection')).toHaveText('normal=' + value + ';pure=' + value + ';blocked=' + value);
  };
  const handles = [];
  for (const id of ['normal', 'pure', 'blocked']) {
    await shown(id, '10:0:initial');
    await expect.poll(() => log(page)).toContain(id + ':mount:10:0:initial');
    handles.push([id, await row(id).elementHandle()]);
  }
  try {
    let offset = await mark();
    await click('Add two to classes');
    for (const id of ['normal', 'pure']) await shown(id, '12:2:initial');
    await shown('blocked', '10:0:initial');
    for (const id of ['normal', 'pure', 'blocked']) await saw(offset, id + ':callback:12:2:initial');
    await saw(offset, 'normal:commit:normal:10:0:initial:born=1->normal:12:2:initial:born=1');
    expect((await since(offset)).filter(entry => entry === 'blocked:render' || entry.startsWith('blocked:commit:'))).toEqual([]);
    await inspect('12:2:initial');
    await click('Force blocked class');
    await shown('blocked', '12:2:initial');

    offset = await mark();
    await click('Rerender equal props');
    await expect(page.getByTestId('derived-pulse')).toHaveText('1');
    expect(await since(offset)).toContain('normal:render');
    expect((await since(offset)).filter(entry => entry === 'pure:render' || entry === 'blocked:render')).toEqual([]);

    await click('Advance derived props');
    for (const id of ['normal', 'pure']) await shown(id, '20:2:initial');
    await shown('blocked', '12:2:initial');
    await inspect('20:2:initial');
    await click('Force blocked class');
    await shown('blocked', '20:2:initial');

    await click('Advance derived props');
    for (const id of ['normal', 'pure']) await shown(id, '30:2:<null>');
    await shown('blocked', '20:2:initial');
    await inspect('30:2:<null>');
    await click('Force blocked class');
    await shown('blocked', '30:2:<null>');

    offset = await mark();
    await click('Add hundred to classes');
    for (const id of ['normal', 'pure']) await shown(id, '35:3:<null>');
    await shown('blocked', '30:2:<null>');
    for (const id of ['normal', 'pure', 'blocked']) await saw(offset, id + ':callback:35:3:<null>');
    await inspect('35:3:<null>');
    await click('Force blocked class');
    await shown('blocked', '35:3:<null>');

    await click('Use empty derived patch');
    await click('Add hundred to classes');
    for (const id of ['normal', 'pure']) await shown(id, '135:4:<null>');
    await inspect('135:4:<null>');
    offset = await mark();
    await click('Rerender equal props');
    await expect(page.getByTestId('derived-pulse')).toHaveText('2');
    expect((await since(offset)).filter(entry => entry === 'pure:render')).toEqual([]);
    await click('Force blocked class');
    await shown('blocked', '135:4:<null>');
    for (const [id, original] of handles) {
      expect(original).not.toBeNull();
      expect(await row(id).evaluate((node, before) => node === before, original)).toBe(true);
    }
  } finally {
    for (const [, handle] of handles) await handle?.dispose();
  }
});
