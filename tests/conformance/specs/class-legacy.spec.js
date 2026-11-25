import {test, server as onServer, expect, log} from './bench.js';

test('class-legacy: both native spellings queue updates and observe pending context before Pure and SCU decisions', async ({page, open}) => {
  const warnings = [];
  page.on('console', message => { if (message.type() === 'warning') warnings.push(message.text()); });
  const problems = await open('class-legacy', {expectErrors: true});
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const normal = page.getByTestId('normal'), pure = page.getByTestId('pure');
  await expect(normal).toHaveText('0/11/day'); await expect(pure).toHaveText('0/11/day');
  const nodes = await Promise.all([normal.elementHandle(), pure.elementHandle()]);
  try {
    const initial = await log(page);
    for (const id of ['normal', 'pure']) {
      expect(initial).toContain(`${id}:mount:old:current=0/0/day`);
      expect(initial).toContain(`${id}:mount:unsafe:current=0/0/day`);
      expect(initial.indexOf(`${id}:mount:old:current=0/0/day`))
        .toBeLessThan(initial.indexOf(`${id}:mount:unsafe:current=0/0/day`));
      expect(initial).toContain(`${id}:mount-callback:old:11`);
      expect(initial).toContain(`${id}:mount-callback:unsafe:11`);
    }
    let offset = initial.length;
    await click('Equal legacy props');
    await expect(page.getByTestId('legacy-pulse')).toHaveText('1');
    let updates = (await log(page)).slice(offset);
    expect(updates.filter(value => value.startsWith('pure:receive:'))).toEqual([
      'pure:receive:old:current=0/11/day:next=0/day',
      'pure:receive:unsafe:current=0/11/day:next=0/day'
    ]);
    expect(updates.filter(value => value.startsWith('pure:render:') || value.startsWith('pure:update:'))).toEqual([]);
    await click('Advance legacy props');
    await expect(normal).toHaveText('1/1111/day'); await expect(pure).toHaveText('1/1111/day');
    for (const id of ['normal', 'pure']) {
      for (const variant of ['old', 'unsafe']) {
        expect(await log(page)).toContain(`${id}:update:${variant}:current=0/11/day:next=1/1111/day`);
        expect(await log(page)).toContain(`${id}:receive-callback:${variant}:1111`);
      }
    }
    await click('Block legacy normal');
    offset = (await log(page)).length;
    await click('Advance legacy props');
    await expect(pure).toHaveText('2/2211/day'); await expect(normal).toHaveText('1/1111/day');
    updates = (await log(page)).slice(offset);
    expect(updates.filter(value => value.startsWith('normal:update:') || value.startsWith('normal:render:'))).toEqual([]);
    expect(updates).toContain('normal:receive-callback:old:2211');
    expect(updates).toContain('normal:receive-callback:unsafe:2211');
    await click('Inspect legacy normal');
    await expect(page.getByTestId('legacy-inspection')).toHaveText('2/2211/day');
    await click('Force legacy normal');
    await expect(normal).toHaveText('2/2211/day');
    await click('Change legacy context');
    await expect(normal).toHaveText('2/2211/night'); await expect(pure).toHaveText('2/2211/night');
    for (const id of ['normal', 'pure']) {
      for (const variant of ['old', 'unsafe']) {
        expect(await log(page)).toContain(`${id}:receive:${variant}:current=2/2211/day:next=2/night`);
        expect(await log(page)).toContain(`${id}:update:${variant}:current=2/2211/day:next=2/2211/night`);
      }
    }
    for (const [index, node] of [normal, pure].entries())
      expect(await node.evaluate((current, original) => current === original, nodes[index])).toBe(true);
    expect((await log(page)).filter(value => value.includes(':fallback-'))).toEqual([]);
    // Native warnings remain observable; unrelated errors and broad console filters are forbidden.
    expect(problems).toHaveLength(3);
    expect(warnings).toHaveLength(3);
    for (const method of ['componentWillMount', 'componentWillReceiveProps', 'componentWillUpdate']) {
      expect(problems.filter(value => value.includes(`Using UNSAFE_${method} in strict mode`))).toHaveLength(1);
      expect(warnings.filter(value => value.startsWith(`${method} has been renamed`))).toHaveLength(1);
    }
    expect(problems.every(value => value.startsWith('console: ') && value.includes('LegacyNormal') && value.includes('LegacyPure'))).toBe(true);
  } finally { await Promise.all(nodes.map(node => node.dispose())); }
});

test('class-legacy-interrupted: abandoning a suspended mount discards its queued callbacks and starts with fresh state', async ({page, open}) => {
  const warnings = [];
  page.on('console', message => { if (message.type() === 'warning') warnings.push(message.text()); });
  const problems = await open('class-legacy-interrupted', {expectErrors: true});
  const click = name => page.getByRole('button', {name, exact: true}).click();
  await click('Start attempt A');
  await expect(page.getByTestId('attempt-waiting')).toHaveText('waiting');
  await click('Inspect attempt');
  await expect(page.getByTestId('attempt-inspection')).toHaveText('detached');
  const abandoned = await log(page);
  expect(abandoned).toContain('A:attempt-mount:old:0');
  expect(abandoned).toContain('A:attempt-mount:unsafe:0');
  expect(abandoned).toContain('A:attempt-render:11');
  expect(abandoned.filter(value => value.includes('attempt-callback:') || value.endsWith('attempt-mounted')
    || value.endsWith('attempt-unmounted'))).toEqual([]);
  await click('Abandon attempt');
  await expect(page.getByTestId('attempt-waiting')).toHaveCount(0);
  await click('Release attempt A');
  await click('Start attempt B');
  await expect(page.getByTestId('attempt-waiting')).toHaveText('waiting');
  await click('Release attempt B');
  await expect(page.getByTestId('attempt-B')).toHaveText('B:11:ready');
  await expect(page.getByTestId('attempt-A')).toHaveCount(0);
  await expect(page.getByTestId('attempt-waiting')).toHaveCount(0);
  await click('Inspect attempt');
  await expect(page.getByTestId('attempt-inspection')).toHaveText('B');
  const calls = await log(page);
  expect(calls.filter(value => value.startsWith('A:attempt-callback:') || value === 'A:attempt-mounted'
    || value === 'A:attempt-unmounted')).toEqual([]);
  expect(calls).toContain('B:attempt-mounted');
  expect(calls).toContain('B:attempt-callback:old:11');
  expect(calls).toContain('B:attempt-callback:unsafe:11');
  expect(calls.filter(value => value.includes(':attempt-mount:')).every(value => value.endsWith(':0'))).toBe(true);
  expect(problems).toHaveLength(1); expect(warnings).toHaveLength(1);
  expect(problems[0]).toContain('Using UNSAFE_componentWillMount in strict mode');
  expect(problems[0]).toContain('LegacyInterrupted');
  expect(warnings[0]).toMatch(/^componentWillMount has been renamed/);
});

onServer('class-legacy-server: server rendering processes legacy mount updates without committed lifecycle callbacks', async server => {
  const ordinary = await server.renderLegacy('class-legacy');
  for (const id of ['normal', 'pure']) {
    expect(ordinary.html).toContain(`<output data-testid="${id}">0/11/day</output>`);
    expect(ordinary.log.filter(value => value.startsWith(`${id}:mount:`))).toEqual([
      `${id}:mount:old:current=0/0/day`, `${id}:mount:unsafe:current=0/0/day`
    ]);
    expect(ordinary.log).toContain(`${id}:render:0/11/day`);
  }
  expect(ordinary.log.filter(value => /callback|mounted|committed|:receive:|:update:/.test(value))).toEqual([]);
  const modern = await server.renderLegacy('class-legacy-suppression');
  for (const id of ['derived', 'snapshot']) {
    expect(modern.html).toContain(`<output data-testid="${id}">0/0/day</output>`);
    expect(modern.log.filter(value => value.startsWith(`${id}:mount:`))).toEqual([]);
  }
  expect(modern.html).toContain('<output data-testid="error">0/11/day</output>');
  expect(modern.log).toContain('error:mount:old:current=0/0/day');
  expect(modern.log).toContain('error:mount:unsafe:current=0/0/day');
  expect(modern.log.filter(value => /callback|mounted|committed|:snapshot|:receive:|:update:/.test(value))).toEqual([]);
});

test('class-legacy-suppression: derived props and snapshots suppress all six callbacks but derived errors alone do not', async ({page, open}) => {
  const warnings = [];
  page.on('console', message => { if (message.type() === 'warning') warnings.push(message.text()); });
  const problems = await open('class-legacy-suppression', {expectErrors: true});
  for (const id of ['derived', 'snapshot']) await expect(page.getByTestId(id)).toHaveText('0/0/day');
  await expect(page.getByTestId('error')).toHaveText('0/11/day');
  await page.getByRole('button', {name: 'Advance suppressed props', exact: true}).click();
  for (const id of ['derived', 'snapshot']) await expect(page.getByTestId(id)).toHaveText('1/0/day');
  await expect(page.getByTestId('error')).toHaveText('1/1111/day');
  const calls = await log(page);
  for (const id of ['derived', 'snapshot']) {
    expect(calls.filter(value => value.startsWith(`${id}:mount:`) || value.startsWith(`${id}:receive:`)
      || value.startsWith(`${id}:update:`))).toEqual([]);
    expect(calls).toContain(`${id}:mounted`); expect(calls).toContain(`${id}:committed`);
  }
  expect(calls).toContain('snapshot:snapshot');
  for (const variant of ['old', 'unsafe']) {
    expect(calls).toContain(`error:mount:${variant}:current=0/0/day`);
    expect(calls).toContain(`error:receive:${variant}:current=0/11/day:next=1/day`);
    expect(calls).toContain(`error:update:${variant}:current=0/11/day:next=1/1111/day`);
  }
  expect(problems).toHaveLength(5); expect(warnings).toHaveLength(3);
  const suppression = problems.filter(value => value.includes('Unsafe legacy lifecycles will not be called'));
  expect(suppression).toHaveLength(2);
  expect(suppression.some(value => value.includes('LegacyDerived') && value.includes('getDerivedStateFromProps'))).toBe(true);
  expect(suppression.some(value => value.includes('LegacySnapshot') && value.includes('getSnapshotBeforeUpdate'))).toBe(true);
  expect(problems.every(value => value.startsWith('console: ') &&
    (value.includes('Unsafe legacy lifecycles will not be called') || value.includes('Using UNSAFE_componentWill')))).toBe(true);
  for (const method of ['componentWillMount', 'componentWillReceiveProps', 'componentWillUpdate'])
    expect(warnings.filter(value => value.startsWith(`${method} has been renamed`))).toHaveLength(1);
});
