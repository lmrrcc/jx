import {test, expect, log} from './bench.js';

test('class-nullable-state: native null aggregates, sparse properties and undefined preserve queue and Pure semantics', async ({page, open}) => {
  await open('class-nullable-state');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const since = async start => (await log(page)).slice(start);
  const native = id => page.evaluate(id => {
    const state = globalThis.__nullableClassOwners.get(id).state;
    return state === null ? {null: true} : {
      null: false, keys: Object.keys(state),
      count: !Object.hasOwn(state, 'count') ? 'absent' : String(state.count),
      note: !Object.hasOwn(state, 'note') ? 'absent' : state.note === undefined ? 'undefined' : state.note === null ? 'null' : String(state.note)
    };
  }, id);
  const callback = (id, operation, state, same) => `${id}:callback:${operation}:${state}:same=${same}`;
  const run = async (id, operation, state, same, renders) => {
    const start = (await log(page)).length;
    await click(id + ' ' + operation);
    const done = callback(id, operation, state, same);
    await expect.poll(() => since(start)).toContain(done);
    const entries = await since(start);
    expect(entries.some(entry => entry === id + ':render')).toBe(renders > 0);
    expect(entries.filter(entry => entry.startsWith(id + ':callback:'))).toEqual([done]);
    expect(entries.findIndex(entry => entry.startsWith(id + ':handler:'))).toBeLessThan(entries.indexOf(done));
    return entries;
  };
  const nodes = await Promise.all(['normal', 'pure', 'blocked'].map(id => page.getByTestId('nullable-' + id).elementHandle()));
  try {
    await expect(page.getByTestId('nullable-seed')).toHaveText('count=absent;note=undefined');
    expect(await native('seed')).toEqual({null: false, keys: ['note'], count: 'absent', note: 'undefined'});
    for (const id of ['normal', 'pure', 'blocked']) {
      await expect(page.getByTestId('nullable-' + id)).toHaveText('null');
      expect(await native(id)).toEqual({null: true});
      for (const operation of ['direct', 'functional']) {
        const entries = await run(id, operation, 'null', true, 0);
        expect(entries).toContain(id + ':handler:' + operation + ':null:same=true');
        expect(entries.some(entry => entry.startsWith(id + ':should:') || entry.startsWith(id + ':commit:'))).toBe(false);
        if (operation === 'functional') expect(entries).toContain(id + ':updater:null');
      }
      const first = await run(id, 'empty', 'count=absent;note=absent', false, id === 'blocked' ? 0 : 1);
      expect(await native(id)).toEqual({null: false, keys: [], count: 'absent', note: 'absent'});
      if (id === 'normal') expect(first).toContain('normal:commit:null->count=absent;note=absent:snapshot=null');
      await run(id, 'empty', 'count=absent;note=absent', false, id === 'normal' ? 1 : 0);
      await run(id, 'partial', 'count=1;note=absent', false, id === 'blocked' ? 0 : 1);
      expect(await native(id)).toEqual({null: false, keys: ['count'], count: '1', note: 'absent'});
      await run(id, 'undefined', 'count=1;note=undefined', false, id === 'blocked' ? 0 : 1);
      expect(await native(id)).toEqual({null: false, keys: ['count', 'note'], count: '1', note: 'undefined'});
      await run(id, 'undefined', 'count=1;note=undefined', false, id === 'normal' ? 1 : 0);
      await run(id, 'null-field', 'count=1;note=null', false, id === 'blocked' ? 0 : 1);
      expect(await native(id)).toEqual({null: false, keys: ['count', 'note'], count: '1', note: 'null'});
      const queue = await run(id, 'queued', 'count=3;note=null', false, id === 'blocked' ? 0 : 1);
      expect(queue).toContain(id + ':updater:first:count=1;note=null');
      expect(queue).toContain(id + ':updater:second:count=2;note=null');
      expect(await native(id)).toEqual({null: false, keys: ['count', 'note'], count: '3', note: 'null'});
      await expect(page.getByTestId('nullable-' + id)).toHaveText(id === 'blocked' ? 'null' : 'count=3;note=null');
      const original = nodes[['normal', 'pure', 'blocked'].indexOf(id)];
      expect(await page.getByTestId('nullable-' + id).evaluate((node, old) => node === old, original)).toBe(true);
    }
    await click('Remount nullable classes');
    for (const id of ['normal', 'pure', 'blocked']) {
      await expect(page.getByTestId('nullable-' + id)).toHaveText('null');
      expect(await native(id)).toEqual({null: true});
      const original = nodes[['normal', 'pure', 'blocked'].indexOf(id)];
      expect(await page.getByTestId('nullable-' + id).evaluate((node, old) => node === old, original)).toBe(false);
    }
    const queue = await run('normal', 'queued', 'count=2;note=absent', false, 1);
    expect(queue).toContain('normal:updater:first:null');
    expect(queue).toContain('normal:updater:second:count=1;note=absent');
  } finally { for (const node of nodes) await node.dispose(); }
});
