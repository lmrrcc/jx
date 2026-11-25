import {test, expect, log} from './bench.js';

test('class-context: native context reaches constructors and bypasses class bailouts while Activity retains the same state and DOM', async ({page, open}) => {
  await open('class-context');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const row = id => page.getByTestId('context-' + id);
  const mark = async () => (await log(page)).length;
  const since = async offset => (await log(page)).slice(offset);
  const saw = async (offset, entry) => expect.poll(() => since(offset)).toContain(entry);
  const identity = async () => {
    await click('Inspect context identity');
    await expect(page.getByTestId('context-inspection')).toHaveText('normal=same;pure=same;default=same;nested=same;activity=same');
  };
  for (const [id, theme] of [['default', 'default'], ['normal', 'outer'], ['pure', 'outer'], ['nested', 'inner'], ['activity', 'outer']]) {
    await expect(row(id)).toHaveText(`${id}:${theme}:0:born=${theme}`);
    await expect.poll(() => log(page)).toContain(`${id}:mount:${theme}:0`);
  }
  await identity();
  const normalNode = await row('normal').elementHandle();
  const pureNode = await row('pure').elementHandle();
  const activityNode = await row('activity').elementHandle();
  expect(normalNode).not.toBeNull();
  expect(pureNode).not.toBeNull();
  expect(activityNode).not.toBeNull();
  try {
    let offset = await mark();
    await click('Increment blocked class');
    await saw(offset, 'normal:callback:1');
    await expect(row('normal')).toHaveText('normal:outer:0:born=outer');
    expect((await since(offset)).filter(entry => entry === 'normal:render' || entry.startsWith('normal:update:'))).toEqual([]);
    await click('Increment pure class');
    await expect(row('pure')).toHaveText('pure:outer:1:born=outer');

    offset = await mark();
    await click('Rerender context host');
    await expect(page.getByTestId('context-pulse')).toHaveText('1');
    // A committed parent update with unchanged provider identity must leave both bailouts intact.
    expect(await since(offset)).toEqual([]);
    offset = await mark();
    await click('Replace equal context');
    await saw(offset, 'normal:update:outer:1');
    await saw(offset, 'pure:update:outer:1');
    await expect(row('normal')).toHaveText('normal:outer:1:born=outer');
    expect((await since(offset)).filter(entry => entry.startsWith('nested:') || entry.startsWith('default:'))).toEqual([]);
    expect(await row('normal').evaluate((node, original) => node === original, normalNode)).toBe(true);
    expect(await row('pure').evaluate((node, original) => node === original, pureNode)).toBe(true);
    await identity();

    offset = await mark();
    await click('Hide activity');
    await expect(row('activity')).toBeHidden();
    await saw(offset, 'activity:unmount:outer:0');
    // Keep a genuine instance reference across Activity's layout cleanup and update it while hidden.
    await click('Increment retained activity');
    await click('Set dark context');
    await expect(row('normal')).toHaveText('normal:dark:1:born=outer');
    await expect(row('pure')).toHaveText('pure:dark:1:born=outer');
    await expect(row('nested')).toHaveText('nested:inner:0:born=inner');
    await expect(row('default')).toHaveText('default:default:0:born=default');
    await click('Show activity');
    await expect(row('activity')).toBeVisible();
    await expect(row('activity')).toHaveText('activity:dark:1:born=outer');
    await saw(offset, 'activity:mount:dark:1');
    await saw(offset, 'activity:callback:1');
    expect(await row('activity').evaluate((node, original) => node === original, activityNode)).toBe(true);
    await click('Inspect retained activity');
    await expect(page.getByTestId('retained-context')).toHaveText('dark;count=1');
    await identity();

    offset = await mark();
    await click('Set null context');
    for (const id of ['normal', 'pure', 'activity']) await expect(row(id)).toHaveText(`${id}:<null>:1:born=outer`);
    await saw(offset, 'normal:update:<null>:1');
    await saw(offset, 'pure:update:<null>:1');
    await expect(row('nested')).toHaveText('nested:inner:0:born=inner');
    await expect(row('default')).toHaveText('default:default:0:born=default');
    await identity();
    offset = await mark();
    await click('Unmount contextual rows');
    for (const id of ['normal', 'pure', 'nested', 'activity']) await expect(row(id)).toHaveCount(0);
    await saw(offset, 'normal:unmount:<null>:1');
    await saw(offset, 'pure:unmount:<null>:1');
    await saw(offset, 'nested:unmount:inner:0');
    await saw(offset, 'activity:unmount:<null>:1');
    expect(await normalNode.evaluate(node => node.isConnected)).toBe(false);
    expect(await pureNode.evaluate(node => node.isConnected)).toBe(false);
    expect(await activityNode.evaluate(node => node.isConnected)).toBe(false);
    // Intentional user retention has native React semantics; it is not a disposal guarantee.
    await click('Inspect retained activity');
    await expect(page.getByTestId('retained-context')).toHaveText('<null>;count=1');
    for (const id of ['normal', 'pure', 'nested', 'activity']) {
      const entries = await log(page);
      expect(entries.filter(entry => entry.startsWith(id + ':mount:')).length).toBeGreaterThan(0);
      expect(entries.filter(entry => entry.startsWith(id + ':unmount:')).length)
        .toBe(entries.filter(entry => entry.startsWith(id + ':mount:')).length);
    }
  } finally {
    await normalNode.dispose();
    await pureNode.dispose();
    await activityNode.dispose();
  }
});
