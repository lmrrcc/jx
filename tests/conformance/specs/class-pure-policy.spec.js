import {test, expect, log} from './bench.js';

test('class-pure-policy: custom policies override Pure comparison while context and force updates retain native behavior', async ({page, open}) => {
  const problems = await open('class-pure-policy', {expectErrors: true});
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const ids = ['legacy', 'context'];
  const nodes = await Promise.all(ids.map(id => page.getByTestId(id).elementHandle()));
  try {
    for (const id of ids) await expect(page.getByTestId(id)).toHaveText('day:0:0:true');
    const before = await log(page);
    await click('Same props');
    await expect(page.getByTestId('pulse')).toHaveText('1');
    for (const id of ids) expect((await log(page)).filter(entry => entry === 'pure-render:' + id).length)
      .toBeGreaterThan(before.filter(entry => entry === 'pure-render:' + id).length);
    await click('Block policy');
    const blocked = await log(page);
    for (const id of ids) {
      await click('Increment ' + id);
      await expect.poll(() => log(page)).toContain('pure-callback:' + id + ':day:0:1:false:dom=day:0:0:true');
      await expect(page.getByTestId(id)).toHaveText('day:0:0:true');
    }
    await click('Advance props');
    expect((await log(page)).filter(entry => entry.startsWith('pure-render:')))
      .toEqual(blocked.filter(entry => entry.startsWith('pure-render:')));
    for (const id of ids) {
      await click('Force ' + id);
      await expect(page.getByTestId(id)).toHaveText('day:1:1:false');
    }
    await click('Change theme');
    for (const id of ids) await expect(page.getByTestId(id)).toHaveText('night:1:1:false');
    expect(await log(page)).toContain('pure-policy:context:day->night');
    await click('Allow policy');
    for (let index = 0; index < ids.length; index++) {
      await expect(page.getByTestId(ids[index])).toHaveText('night:1:1:true');
      expect(await page.getByTestId(ids[index]).evaluate((current, original) => current === original, nodes[index])).toBe(true);
    }
    // Keep React's warning observable; tolerate only that documented native warning.
    expect(problems.length).toBeGreaterThanOrEqual(2);
    expect(problems.every(message => message.startsWith('console: ') && message.includes('shouldComponentUpdate')
      && message.includes('React.PureComponent'))).toBe(true);
  } finally { await Promise.all(nodes.map(node => node.dispose())); }
});
