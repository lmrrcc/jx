import {test, expect, log} from './bench.js';

test('custom-hook: two calls of one custom hook keep independent state and effects', async ({page, open}) => {
  await open('custom-hook');
  // Strict Mode in development runs each mount effect twice.
  await expect.poll(() => log(page)).toEqual(['ones 0', 'tens 0', 'ones 0', 'tens 0']);
  await page.getByRole('button', {name: 'Advance tens'}).click();
  await expect(page.getByTestId('tens')).toHaveText('10');
  await expect(page.getByTestId('ones')).toHaveText('0');
  await page.getByRole('button', {name: 'Advance ones'}).click();
  await expect(page.getByTestId('ones')).toHaveText('1');
  await expect.poll(() => log(page)).toEqual(['ones 0', 'tens 0', 'ones 0', 'tens 0', 'tens 10', 'ones 1']);
});

test('function-props: a callback prop reaches the parent and a render prop produces content', async ({page, open}) => {
  await open('function-props');
  const rendered = page.getByTestId('rendered');
  await expect(rendered).toHaveText('value 0');
  expect(await rendered.evaluate(node => node.tagName)).toEqual('EM');
  await page.getByRole('button', {name: 'Step'}).click();
  await page.getByRole('button', {name: 'Step'}).click();
  await expect(rendered).toHaveText('value 2');
  await page.getByRole('button', {name: 'Use strong'}).click();
  await expect(rendered).toHaveText('value 2');
  expect(await rendered.evaluate(node => node.tagName)).toEqual('STRONG');
});

test('dynamic-component: the type is chosen at run time and a new type remounts', async ({page, open}) => {
  await open('dynamic-component');
  await expect(page.getByRole('region', {name: 'Quiet'})).toBeVisible();
  await page.getByRole('button', {name: 'Increment'}).click();
  await page.getByRole('button', {name: 'Rerender'}).click();
  await expect(page.getByTestId('renders')).toHaveText('1');
  // Same type across a parent rerender: state is kept.
  await expect(page.getByRole('status')).toHaveText('1');
  await page.getByRole('button', {name: 'Switch type'}).click();
  await expect(page.getByRole('region', {name: 'Loud'})).toBeVisible();
  await expect(page.getByRole('region', {name: 'Quiet'})).toHaveCount(0);
  // A different type in the same position is a new instance.
  await expect(page.getByRole('status')).toHaveText('0');
  await page.getByRole('button', {name: 'Switch type'}).click();
  await expect(page.getByRole('region', {name: 'Quiet'}).getByRole('status')).toHaveText('0');
});

test('namespace-components: a component is referenced by a dotted name', async ({page, open}) => {
  await open('namespace-components');
  const panel = page.getByRole('region', {name: 'Qualified'});
  await expect(panel.getByRole('heading')).toHaveText('Qualified');
  await expect(panel.getByTestId('inside')).toHaveText('inside');
});

test('memo-skip: a memoized component skips rendering when props are equal', async ({page, open}) => {
  await open('memo-skip');
  const mount = ['render a', 'render a', 'render compared a', 'render compared a'];
  expect(await log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Rerender parent'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  // The custom comparison ignores the changed tick, so the stale tick stays.
  expect(await log(page)).toEqual(mount);
  await expect(page.getByTestId('compared')).toHaveText('a 0');
  await page.getByRole('button', {name: 'Change label'}).click();
  await expect(page.getByTestId('row')).toHaveText('b');
  await expect(page.getByTestId('compared')).toHaveText('b 1');
  expect(await log(page)).toEqual([...mount, 'render b', 'render b', 'render compared b', 'render compared b']);
});
