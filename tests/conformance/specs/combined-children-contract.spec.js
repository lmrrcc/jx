import {test, expect, interaction} from './bench.js';

test('combined-children-contract: fragments stay opaque through children props while arrays are traversed and only requires an element', async ({page, open}) => {
  await open('combined-children-contract');
  const stats = async (name, count, listed, visited, only) => {
    await expect(page.getByTestId(name + '-count')).toHaveText(String(count));
    await expect(page.getByTestId(name + '-listed')).toHaveText(String(listed));
    await expect(page.getByTestId(name + '-visited')).toHaveText(String(visited));
    await expect(page.getByTestId(name + '-only')).toHaveText(only);
  };
  const mapped = name => page.getByTestId(name + '-mapped').locator(':scope > li');

  await interaction('children-fragment', async () => {
    await stats('direct-fragment', 1, 1, 1, 'accepted');
    await expect(mapped('direct-fragment')).toHaveCount(1);
    await expect(mapped('direct-fragment').locator('b')).toHaveText('left');
    await expect(mapped('direct-fragment').locator('i')).toHaveText('right');
    await stats('empty-fragment', 1, 1, 1, 'accepted');
    await expect(mapped('empty-fragment')).toHaveCount(1);
    await expect(mapped('empty-fragment')).toHaveText('');
  });

  await interaction('children-prop', async () => {
    // Passing the fragment through a component must not turn its contents into two children.
    await stats('prop-fragment', 1, 1, 1, 'accepted');
    await expect(mapped('prop-fragment')).toHaveCount(1);
    await expect(mapped('prop-fragment')).toHaveText('leftright');
    await expect(mapped('prop-fragment').locator('b')).toHaveText('left');
    await expect(mapped('prop-fragment').locator('i')).toHaveText('right');
    // The explicit children attribute must have the same contract as nested markup.
    await stats('attribute-fragment', 1, 1, 1, 'accepted');
    await expect(mapped('attribute-fragment')).toHaveCount(1);
    await expect(mapped('attribute-fragment')).toHaveText('leftright');
    await stats('element', 1, 1, 1, 'accepted');
    await expect(mapped('element').locator('em')).toHaveText('one');
  });

  await interaction('children-values', async () => {
    // Nested arrays are traversed; empty positions count but are absent from toArray/toList.
    await stats('nested', 5, 3, 5, 'rejected');
    await expect(mapped('nested')).toHaveText(['A', '', '', 'text', 'B']);
    await stats('singleton-array', 1, 1, 1, 'rejected');
    await expect(mapped('singleton-array').locator('em')).toHaveText('array');
    await stats('text', 1, 1, 1, 'rejected');
    await expect(mapped('text')).toHaveText('text');
    await stats('null', 0, 0, 0, 'rejected');
    await expect(mapped('null')).toHaveCount(0);
    // Expected spelling comes from native JavaScript; JX computes it in the compiled Java library.
    const numbers = [1.0, -0.0, 1e-6, 1e-7, 1e20, 1e21, 1e23, Number.MIN_VALUE, Number.MAX_VALUE,
      Math.fround(1.1), NaN, Infinity, -Infinity, Number.MAX_SAFE_INTEGER];
    const key = value => '.$' + String(value).replaceAll('=', '=0').replaceAll(':', '=2');
    await expect(page.getByTestId('numeric-keys')).toHaveText(numbers.map(key).join('|'));
    await expect(page.getByTestId('escaped-keys')).toHaveText(['a:b=c', '=:', 'slash/key'].map(key).join('|'));
    // Portal construction needs the browser, so inspect it only after this user event.
    await expect(page.getByTestId('native-nodes')).toHaveText('uninspected');
    await page.getByRole('button', {name: 'Inspect native nodes', exact: true}).click();
    await expect(page.getByTestId('native-nodes')).toHaveText('strict true accepted 1; portal false rejected 1');
    await expect(page.getByTestId('native-strict-content')).toHaveCount(0);
    await expect(page.getByTestId('native-portal-content')).toHaveCount(0);
  });
});
