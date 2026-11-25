import {test, expect, log} from './bench.js';

test('class-sparse-authoring: generated null and Presence state preserve native shape, queues and Pure decisions', async ({page, open}) => {
  await open('class-sparse-authoring');
  const native = id => page.evaluate(id => {
    const state = globalThis.__nullableClassOwners.get(id).state;
    if (state === null) return {null: true};
    const value = name => !Object.hasOwn(state, name) ? 'absent' : state[name] === undefined ? 'undefined'
      : state[name] === null ? 'null' : String(state[name]);
    return {null: false, keys: Object.keys(state), count: value('count'), note: value('note')};
  }, id);
  const run = async (id, operation, before, after, renders) => {
    const start = (await log(page)).length;
    await page.getByRole('button', {name: id + ' ' + operation, exact: true}).click();
    const callback = id + ':callback:' + operation + ':' + after + ':same=false';
    await expect.poll(async () => (await log(page)).slice(start)).toContain(callback);
    const entries = (await log(page)).slice(start);
    const handler = id + ':handler:' + operation + ':' + before + ':same=true';
    expect(entries).toContain(handler);
    expect(entries.indexOf(handler)).toBeLessThan(entries.indexOf(callback));
    expect(entries.filter(entry => entry.startsWith(id + ':callback:'))).toEqual([callback]);
    expect(entries.includes(id + ':render')).toBe(renders);
    if (renders) {
      const commit = id + ':commit:' + before + '->' + after + ':snapshot=' + before;
      expect(entries).toContain(commit);
      expect(entries.indexOf(commit)).toBeLessThan(entries.indexOf(callback));
    } else expect(entries.some(entry => entry.startsWith(id + ':commit:'))).toBe(false);
    await expect(page.getByTestId('sparse-' + id)).toHaveText(after);
    return entries;
  };
  for (const id of ['normal', 'pure']) {
    const output = page.getByTestId('sparse-' + id);
    await expect(output).toHaveText('null');
    expect(await native(id)).toEqual({null: true});
    const originalNode = await output.elementHandle();
    const originalPeer = await page.evaluateHandle(id => globalThis.__nullableClassOwners.get(id), id);
    try {
      const empty = 'count=absent;note=absent';
      await run(id, 'empty', 'null', empty, true);
      expect(await native(id)).toEqual({null: false, keys: [], count: 'absent', note: 'absent'});
      await run(id, 'empty', empty, empty, id === 'normal');
      await run(id, 'partial', empty, 'count=1;note=absent', true);
      expect(await native(id)).toEqual({null: false, keys: ['count'], count: '1', note: 'absent'});
      await run(id, 'undefined', 'count=1;note=absent', 'count=1;note=undefined', true);
      expect(await native(id)).toEqual({null: false, keys: ['count', 'note'], count: '1', note: 'undefined'});
      await run(id, 'undefined', 'count=1;note=undefined', 'count=1;note=undefined', id === 'normal');
      await run(id, 'null-field', 'count=1;note=undefined', 'count=1;note=null', true);
      expect(await native(id)).toEqual({null: false, keys: ['count', 'note'], count: '1', note: 'null'});
      const queued = await run(id, 'queued', 'count=1;note=null', 'count=3;note=null', true);
      expect(queued).toContain(id + ':updater:first:count=1;note=null');
      expect(queued).toContain(id + ':updater:second:count=2;note=null');
      expect(await native(id)).toEqual({null: false, keys: ['count', 'note'], count: '3', note: 'null'});
      expect(await output.evaluate((node, original) => node === original, originalNode)).toBe(true);
      expect(await page.evaluate(({id, original}) => globalThis.__nullableClassOwners.get(id) === original,
        {id, original: originalPeer})).toBe(true);
    } finally {
      await originalNode.dispose();
      await originalPeer.dispose();
    }
  }
});
