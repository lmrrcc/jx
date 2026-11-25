import {test, expect, log} from './bench.js';

test('class-legacy-authoring: declarations compose native variants, typed pending context, defaults and Pure state queues', async ({page, open}) => {
  const warnings = [];
  page.on('console', message => { if (message.type() === 'warning') warnings.push(message.text()); });
  const problems = await open('class-legacy-authoring', {expectErrors: true});
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const normal = page.getByTestId('authored-legacy-normal'), pure = page.getByTestId('authored-legacy-pure');
  await expect(normal).toHaveText('0/11/day/default'); await expect(pure).toHaveText('0/11');
  const nodes = await Promise.all([normal.elementHandle(), pure.elementHandle()]);
  try {
    const initial = await log(page);
    for (const id of ['normal', 'pure']) {
      expect(initial.indexOf(`${id}:mount:old`)).toBeGreaterThanOrEqual(0);
      expect(initial.indexOf(`${id}:mount:old`)).toBeLessThan(initial.indexOf(`${id}:mount:unsafe`));
      for (const variant of ['old', 'unsafe']) expect(initial).toContain(`${id}:mount-callback:${variant}:11`);
    }
    const offset = initial.length;
    await click('Equal authored legacy props');
    await expect(page.getByTestId('authored-legacy-pulse')).toHaveText('1');
    const unchanged = (await log(page)).slice(offset);
    expect(unchanged.filter(value => value.startsWith('pure:receive:'))).toEqual([
      'pure:receive:old:0/11/none->0/none', 'pure:receive:unsafe:0/11/none->0/none'
    ]);
    expect(unchanged.filter(value => value.startsWith('pure:update:') || value === 'pure:render')).toEqual([]);
    await click('Advance authored legacy props');
    await expect(normal).toHaveText('1/1111/day/default'); await expect(pure).toHaveText('1/1111');
    for (const [id, theme] of [['normal', 'day'], ['pure', 'none']]) for (const variant of ['old', 'unsafe']) {
      expect(await log(page)).toContain(`${id}:receive:${variant}:0/11/${theme}->1/${theme}`);
      expect(await log(page)).toContain(`${id}:update:${variant}:0/11/${theme}->1/1111/${theme}`);
      expect(await log(page)).toContain(`${id}:receive-callback:${variant}:1111`);
    }
    let previous = 'day';
    for (const [action, theme] of [['Change authored legacy context', 'night'], ['Null authored legacy context', 'null']]) {
      await click(action);
      await expect(normal).toHaveText(`1/1111/${theme}/default`); await expect(pure).toHaveText('1/1111');
      for (const variant of ['old', 'unsafe']) {
        expect(await log(page)).toContain(`normal:receive:${variant}:1/1111/${previous}->1/${theme}`);
        expect(await log(page)).toContain(`normal:update:${variant}:1/1111/${previous}->1/1111/${theme}`);
      }
      previous = theme;
    }
    for (const [index, node] of [normal, pure].entries())
      expect(await node.evaluate((current, original) => current === original, nodes[index])).toBe(true);
    expect(problems).toHaveLength(3); expect(warnings).toHaveLength(3);
    for (const method of ['componentWillMount', 'componentWillReceiveProps', 'componentWillUpdate']) {
      expect(problems.filter(value => value.includes(`Using UNSAFE_${method} in strict mode`))).toHaveLength(1);
      expect(warnings.filter(value => value.startsWith(`${method} has been renamed`))).toHaveLength(1);
    }
    expect(problems.every(value => value.startsWith('console: ') && value.includes('AuthoredLegacy')
      && value.includes('AuthoredLegacyPure'))).toBe(true);
  } finally { await Promise.all(nodes.map(node => node.dispose())); }
});
