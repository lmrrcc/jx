import {test, expect, log} from './bench.js';

test('class-nullable-derived: derivations see null pending state and preserve sparse merges through Pure and skipped renders', async ({page, open}) => {
  const problems = await open('class-nullable-derived', {expectErrors: true});
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const native = id => page.evaluate(id => {
    const state = globalThis.__nullableClassOwners.get(id).state;
    return state === null ? null : {keys: Object.keys(state), count: state.count ?? 'absent', note: !Object.hasOwn(state, 'note') ? 'absent' : state.note === undefined ? 'undefined' : state.note};
  }, id);
  for (const id of ['normal', 'pure', 'blocked', 'error']) {
    await expect(page.getByTestId('nullable-' + id)).toHaveText('null');
    expect(await native(id)).toBeNull();
  }
  for (const id of ['normal', 'pure', 'blocked']) {
    expect(await log(page)).toContain(id + ':derive:0:null');
    let start = (await log(page)).length;
    await click(id + ' direct');
    await expect.poll(async () => (await log(page)).slice(start)).toContain(id + ':callback:direct:null:same=true');
    expect(await native(id)).toBeNull();
  }
  await expect.poll(() => problems.filter(problem => problem.includes('uses `getDerivedStateFromProps`')).length).toBe(2);
  expect(problems).toHaveLength(2);
  expect(problems.every(problem => problem.includes('uses `getDerivedStateFromProps`')
    && problem.includes('initial state is') && /\bnull\b/.test(problem)), problems.join('\n')).toBe(true);
  expect(problems.some(problem => problem.includes('NullableDerivedNormal'))).toBe(true);
  expect(problems.some(problem => problem.includes('NullableDerivedPure'))).toBe(true);

  let start = (await log(page)).length;
  await click('Advance nullable derivation');
  await expect(page.getByTestId('nullable-revision')).toHaveText('1');
  for (const id of ['normal', 'pure', 'blocked']) {
    expect(await native(id)).toEqual({keys: ['count'], count: 10, note: 'absent'});
    expect((await log(page)).slice(start)).toContain(id + ':derive:1:null');
    await expect(page.getByTestId('nullable-' + id)).toHaveText(id === 'blocked' ? 'null' : 'count=10;note=absent');
  }
  expect((await log(page)).slice(start)).toContain('normal:commit:null->count=10;note=absent:snapshot=null');
  for (const id of ['normal', 'pure', 'blocked']) {
    start = (await log(page)).length;
    await click(id + ' queued');
    await expect.poll(async () => (await log(page)).slice(start)).toContain(id + ':callback:queued:count=10;note=absent:same=false');
    const entries = (await log(page)).slice(start);
    expect(entries).toContain(id + ':updater:first:count=10;note=absent');
    expect(entries).toContain(id + ':updater:second:count=11;note=absent');
    expect(entries).toContain(id + ':derive:1:count=12;note=absent');
    expect(entries.some(entry => entry === id + ':render')).toBe(id === 'normal');
  }
  for (const [revision, note] of [[2, null], [3, 'undefined'], [4, 'undefined']]) {
    await click('Advance nullable derivation');
    await expect(page.getByTestId('nullable-revision')).toHaveText(String(revision));
    for (const id of ['normal', 'pure', 'blocked'])
      expect(await native(id)).toEqual({keys: ['count', 'note'], count: 10, note});
  }
  start = (await log(page)).length;
  await click('Fail nullable child');
  await expect(page.getByTestId('nullable-error')).toHaveText('count=absent;note=caught');
  expect(await native('error')).toEqual({keys: ['note'], count: 'absent', note: 'caught'});
  const caught = (await log(page)).slice(start).filter(entry => entry.startsWith('error:caught:'));
  expect(caught).toEqual(['error:caught:nullable child failed:state=count=absent;note=caught:stack=true']);
  expect(problems.filter(problem => !problem.includes('uses `getDerivedStateFromProps`')))
    .toEqual([expect.stringContaining('nullable child failed')]);
});
