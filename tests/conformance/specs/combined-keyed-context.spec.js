import {test, expect, log, interaction} from './bench.js';

test('combined-keyed-context: memoized keyed rows retain drafts and state while context owns effect cleanup', async ({page, open}) => {
  await open('combined-keyed-context');
  const row = id => page.locator(`li[data-row="${id}"]`);
  const count = id => row(id).getByRole('status');
  const draft = id => page.getByRole('textbox', {name: 'Draft ' + id});
  const order = () => page.locator('li[data-row]').evaluateAll(rows => rows.map(node => node.dataset.row));
  const effects = async () => (await log(page)).filter(entry => !entry.startsWith('render '));
  const renders = async id => (await log(page)).filter(entry => entry.startsWith('render ' + id + ' '));
  // Wait for Strict Mode's initial setup/cleanup/setup cycle before measuring later lifetimes.
  await expect.poll(async () => (await effects()).filter(entry => entry === 'setup b light').length).toBe(2);
  const mountedEffects = await effects();
  await draft('a').fill('draft a');
  await draft('b').fill('draft b');
  await page.getByRole('button', {name: 'Increment a'}).click();
  await page.getByRole('button', {name: 'Increment a'}).click();
  await page.getByRole('button', {name: 'Increment b'}).click();

  await interaction(['memo-props'], async () => {
    const before = await log(page);
    await page.getByRole('button', {name: 'Rerender parent'}).click();
    await expect(page.getByTestId('parent-tick')).toHaveText('1');
    expect(await log(page)).toEqual(before);
    await page.getByRole('button', {name: 'Change labels'}).click();
    await expect(row('a').getByRole('heading')).toHaveText('Updated a');
    await expect(row('b').getByRole('heading')).toHaveText('Updated b');
    expect((await renders('a')).at(-1)).toEqual('render a Updated light');
    expect((await renders('b')).at(-1)).toEqual('render b Updated light');
    await expect(count('a')).toHaveText('2');
  });

  await interaction(['key-state', 'key-input', 'key-effect'], async () => {
    const before = mountedEffects;
    await draft('a').evaluate(node => { node.dataset.retained = 'yes'; });
    await page.getByRole('button', {name: 'Reverse rows'}).click();
    await expect.poll(order).toEqual(['b', 'a']);
    await expect(count('a')).toHaveText('2');
    await expect(count('b')).toHaveText('1');
    await expect(draft('a')).toHaveValue('draft a');
    await expect(draft('b')).toHaveValue('draft b');
    await expect(draft('a')).toHaveAttribute('data-retained', 'yes');
    expect(await effects()).toEqual(before);
    await page.getByRole('button', {name: 'Remove a'}).click();
    await expect.poll(order).toEqual(['b']);
    await expect.poll(effects).toEqual([...before, 'cleanup a light']);
    await expect(count('b')).toHaveText('1');
    await page.getByRole('button', {name: 'Restore a'}).click();
    await expect.poll(order).toEqual(['b', 'a']);
    await expect(count('a')).toHaveText('0');
    await expect(draft('a')).toHaveValue('');
    expect(await draft('a').getAttribute('data-retained')).toBeNull();
    await expect(count('b')).toHaveText('1');
    await expect(draft('b')).toHaveValue('draft b');
    await expect.poll(async () => (await effects()).filter(entry => entry === 'setup a light').length).toBe(4);
  });

  await page.getByRole('button', {name: 'Increment a'}).click();
  await draft('a').fill('restored draft');
  await interaction(['memo-context', 'context-state', 'context-effect'], async () => {
    const before = await effects();
    const aRenders = (await renders('a')).length;
    const bRenders = (await renders('b')).length;
    await page.getByRole('button', {name: 'Change theme'}).click();
    await expect(page.getByTestId('theme-a')).toHaveText('dark');
    await expect(page.getByTestId('theme-b')).toHaveText('dark');
    expect((await renders('a')).length).toBeGreaterThan(aRenders);
    expect((await renders('b')).length).toBeGreaterThan(bRenders);
    await expect(count('a')).toHaveText('1');
    await expect(count('b')).toHaveText('1');
    await expect(draft('a')).toHaveValue('restored draft');
    await expect(draft('b')).toHaveValue('draft b');
    for (const id of ['a', 'b']) {
      await expect.poll(async () => (await effects()).slice(before.length).filter(entry => entry.includes(' ' + id + ' ')))
        .toEqual(['cleanup ' + id + ' light', 'setup ' + id + ' dark']);
    }
  });
});
