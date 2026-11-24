import {test, expect, interaction} from './bench.js';

test('combined-children-map: source and wrapper keys retain mapped child state and returned arrays get separate identities', async ({page, open}) => {
  await open('combined-children-map');
  const row = id => page.locator(`[data-child="${id}"]`);
  const increment = async id => row(id).getByRole('button').click();
  await interaction(['children-key', 'children-state'], async () => {
    await increment('a');
    await increment('a');
    await increment('b');
    await row('a').getByRole('textbox').fill('retained a');
    await row('a').evaluate(node => { node.dataset.retained = 'yes'; });
    await page.getByRole('button', {name: 'Reverse', exact: true}).click();
    await expect.poll(() => page.locator('[data-child]').evaluateAll(nodes => nodes.map(node => node.dataset.child)))
      .toEqual(['b', 'a']);
    await expect(row('a').locator('output')).toHaveText('2');
    await expect(row('b').locator('output')).toHaveText('1');
    await expect(row('a').getByRole('textbox')).toHaveValue('retained a');
    await expect(row('a')).toHaveAttribute('data-retained', 'yes');
    await page.getByRole('button', {name: 'Toggle a', exact: true}).click();
    await expect(row('a')).toHaveCount(0);
    await expect(row('b').locator('output')).toHaveText('1');
    await page.getByRole('button', {name: 'Toggle a', exact: true}).click();
    await expect(row('a').locator('output')).toHaveText('0');
    await expect(row('b').locator('output')).toHaveText('1');
    await page.getByRole('button', {name: 'Change wrapper key'}).click();
    await expect(row('b').locator('output')).toHaveText('0');

    await page.getByRole('button', {name: 'Toggle copies'}).click();
    await expect(row('a')).toHaveCount(2);
    const copy = (side, id) => page.locator(`[data-copy="${side}"] [data-child="${id}"]`);
    await copy('left', 'a').getByRole('button').click();
    await copy('right', 'b').getByRole('button').click();
    await copy('right', 'b').getByRole('button').click();
    await page.getByRole('button', {name: 'Reverse', exact: true}).click();
    await expect(copy('left', 'a').locator('output')).toHaveText('1');
    await expect(copy('right', 'a').locator('output')).toHaveText('0');
    await expect(copy('left', 'b').locator('output')).toHaveText('0');
    await expect(copy('right', 'b').locator('output')).toHaveText('2');
  });
});

test('combined-children-list: flattening nested arrays qualifies reused local keys and preserves state within each group', async ({page, open}) => {
  await open('combined-children-list');
  await interaction('children-list', async () => {
    const row = id => page.locator(`[data-child="${id}"]`);
    for (const [id, count] of [['first-a', 1], ['first-b', 2], ['second-a', 3]]) {
      for (let i = 0; i < count; i++) await row(id).getByRole('button').click();
      await row(id).getByRole('textbox').fill(id);
    }
    await page.getByRole('button', {name: 'Reverse first group'}).click();
    await expect.poll(() => page.locator('[data-child]').evaluateAll(nodes => nodes.map(node => node.dataset.child)))
      .toEqual(['first-b', 'first-a', 'second-a']);
    for (const [id, count] of [['first-a', 1], ['first-b', 2], ['second-a', 3]]) {
      await expect(row(id).locator('output')).toHaveText(String(count));
      await expect(row(id).getByRole('textbox')).toHaveValue(id);
    }
  });
});
