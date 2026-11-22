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

test('spread-props: spread attributes and props follow source order', async ({page, open}) => {
  await open('spread-props');
  const button = page.getByRole('button');
  await expect(button).toHaveAttribute('data-kind', 'spread');
  await expect(button).toHaveAttribute('title', 'explicit wins');
  await expect(page.getByText('Earlier loses')).toHaveAttribute('title', 'from spread');
  await expect(page.getByTestId('label')).toHaveText('spread text');
  await expect(page.getByTestId('label')).toHaveAttribute('data-tone', 'calm');
});

test('default-props: an omitted prop takes its default and an absent one adds no attribute', async ({page, open}) => {
  await open('default-props');
  const fallback = page.getByTestId('badge-default-label');
  await expect(fallback).toHaveText('default label');
  expect(await fallback.evaluate(node => node.hasAttribute('data-tone'))).toBe(false);
  await expect(page.getByTestId('badge-given')).toHaveAttribute('data-tone', 'warm');
});

test('generic-list: one component renders lists of different item types', async ({page, open}) => {
  await open('generic-list');
  expect(await page.locator('li').allTextContents()).toEqual(['X', 'Y', '1:one', '2:two']);
});


test('is-valid-element: elements and fragments are valid elements; text, numbers and null are not', async ({page, open}) => {
  await open('is-valid-element');
  await expect(page.getByRole('status')).toHaveText('true true false false false');
});

test('children-utilities: Children counts, lists, maps and singles out the children it is given', async ({page, open}) => {
  await open('children-utilities');
  // A nested list is flattened; the empty child is counted but not listed.
  await expect(page.getByRole('status')).toHaveText('4/3');
  // map visits the empty child too.
  expect(await page.locator('li').allTextContents()).toEqual(['a', '', 'b', 'c']);
  await expect(page.getByTestId('only').locator('em')).toHaveText('one');
});

test('clone-element: a cloned element merges new props and children', async ({page, open}) => {
  await open('clone-element');
  const button = page.getByRole('button');
  await expect(button).toHaveText('Cloned');
  await expect(button).toHaveClass('cloned');
  await expect(button).toHaveAttribute('title', 'original');
  await expect(button).toHaveAttribute('data-extra', 'yes');
});

test('lazy-component: a lazy component shows the Suspense fallback until it loads', async ({page, open}) => {
  await open('lazy-component');
  await expect(page.getByRole('status')).toHaveText('loading');
  await page.getByRole('button', {name: 'Load'}).click();
  await expect(page.getByRole('status')).toHaveText('late content');
});
