import {test, expect} from './bench.js';

test('keyed-list: state follows the key on reorder and resets after removal', async ({page, open}) => {
  await open('keyed-list');
  const order = () => page.locator('li').evaluateAll(items => items.map(item => item.dataset.row));
  const count = name => page.locator(`li[data-row="${name}"]`).getByRole('status');
  expect(await order()).toEqual(['a', 'b', 'c']);
  await page.getByRole('button', {name: 'Increment a'}).click();
  await page.getByRole('button', {name: 'Increment a'}).click();
  await page.getByRole('button', {name: 'Increment c'}).click();
  // The DOM node itself must move, not be recreated.
  await page.locator('li[data-row="a"]').evaluate(node => { node.dataset.marked = 'yes'; });
  await page.getByRole('button', {name: 'Reverse'}).click();
  await expect.poll(order).toEqual(['c', 'b', 'a']);
  await expect(count('a')).toHaveText('2');
  await expect(count('b')).toHaveText('0');
  await expect(count('c')).toHaveText('1');
  await expect(page.locator('li[data-row="a"]')).toHaveAttribute('data-marked', 'yes');
  await page.getByRole('button', {name: 'Remove a'}).click();
  await expect.poll(order).toEqual(['c', 'b']);
  await page.getByRole('button', {name: 'Restore a'}).click();
  await expect.poll(order).toEqual(['c', 'b', 'a']);
  await expect(count('a')).toHaveText('0');
  await expect(count('c')).toHaveText('1');
});

test('remount-by-key: a new key discards the old instance and its state', async ({page, open}) => {
  await open('remount-by-key');
  await page.getByRole('button', {name: 'Increment item'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  await page.getByRole('button', {name: 'New key'}).click();
  await expect(page.getByRole('status')).toHaveText('0');
});

test('props-update: child state survives parent rerender and new props', async ({page, open}) => {
  await open('props-update');
  await page.getByRole('button', {name: 'Increment child'}).click();
  await page.getByRole('button', {name: 'Rerender parent'}).click();
  await expect(page.getByTestId('ticks')).toHaveText('1');
  await expect(page.getByRole('status')).toHaveText('1');
  await page.getByRole('button', {name: 'Change label'}).click();
  await expect(page.getByRole('region', {name: 'after'})).toBeVisible();
  await expect(page.getByRole('heading', {name: 'after'})).toBeVisible();
  await expect(page.getByRole('status')).toHaveText('1');
});

test('conditional: branches mount and unmount; null renders nothing', async ({page, open}) => {
  await open('conditional');
  await expect(page.getByTestId('branch')).toHaveText('first');
  await page.getByRole('button', {name: 'Next'}).click();
  await expect(page.getByTestId('branch')).toHaveText('second');
  await page.getByRole('button', {name: 'Next'}).click();
  await expect(page.getByTestId('branch')).toHaveCount(0);
  expect(await page.locator('main').evaluate(node => node.children.length)).toEqual(1);
  await page.getByRole('button', {name: 'Next'}).click();
  await expect(page.getByTestId('branch')).toHaveText('first');
});

test('fragment: groups children without adding an element', async ({page, open}) => {
  await open('fragment');
  const items = () => page.locator('ul').evaluate(list => [...list.children].map(child => child.tagName + ':' + child.textContent));
  expect(await items()).toEqual(['LI:one', 'LI:two']);
  await page.getByRole('button', {name: 'More'}).click();
  await expect.poll(items).toEqual(['LI:one', 'LI:two', 'LI:three', 'LI:four']);
});

test('children-prop: nested markup is passed as children and updates with the parent', async ({page, open}) => {
  await open('children-prop');
  const box = page.getByRole('region', {name: 'Box'});
  await expect(box.getByRole('heading')).toHaveText('Box');
  await expect(box.getByTestId('first')).toHaveText('first');
  await page.getByRole('button', {name: 'Increment'}).click();
  await expect(box.getByRole('status')).toHaveText('1');
  expect(await box.evaluate(node => [...node.children].map(child => child.tagName))).toEqual(['H2', 'P', 'P']);
});

test('text-escaping: strings are text, never markup; entities decode', async ({page, open}) => {
  await open('text-escaping');
  await expect(page.getByTestId('string')).toHaveText('<b>bold</b><img src=x onerror=alert(1)>');
  expect(await page.getByTestId('string').evaluate(node => node.children.length)).toEqual(0);
  expect(await page.getByTestId('entities').evaluate(node => node.textContent)).toEqual('& < > " \' A B');
  await expect(page.getByTestId('attribute')).toHaveAttribute('title', 'a & b');
});

test('child-values: null and booleans render nothing; scalars and nested collections render as text', async ({page, open}) => {
  await open('child-values');
  const text = id => page.getByTestId(id).evaluate(node => node.textContent);
  expect(await text('empty')).toEqual('');
  expect(await text('scalars')).toEqual('42x1.5');
  expect(await text('nested')).toEqual('abcde');
});

test('attributes: aliases, data and aria names, booleans, numbers and style', async ({page, open}) => {
  await open('attributes');
  const label = page.locator('label');
  await expect(label).toHaveAttribute('for', 'field');
  await expect(label).toHaveClass('label wide');
  await expect(label).toHaveAttribute('data-kind', 'primary');
  await expect(label).toHaveAttribute('aria-label', 'Field label');
  const field = page.locator('#field');
  await expect(field).toHaveValue('fixed');
  await expect(field).toHaveAttribute('readonly', '');
  await expect(field).toHaveAttribute('tabindex', '3');
  await expect(page.getByRole('button', {name: 'Disabled'})).toBeDisabled();
  await expect(page.getByRole('button', {name: 'Enabled'})).toBeEnabled();
  expect(await page.getByRole('button', {name: 'Enabled'}).evaluate(node => node.hasAttribute('disabled'))).toBe(false);
  const styled = page.getByTestId('styled');
  await expect(styled).toHaveCSS('color', 'rgb(255, 0, 0)');
  await expect(styled).toHaveCSS('opacity', '0.5');
});

test('svg: elements are in the SVG namespace with the right attribute names', async ({page, open}) => {
  await open('svg');
  const svg = page.getByRole('img', {name: 'Line'});
  expect(await svg.evaluate(node => node.namespaceURI)).toEqual('http://www.w3.org/2000/svg');
  await expect(svg).toHaveAttribute('viewBox', '0 0 20 20');
  const path = svg.locator('path');
  expect(await path.evaluate(node => node.namespaceURI)).toEqual('http://www.w3.org/2000/svg');
  await expect(path).toHaveAttribute('stroke-width', '2');
  await expect(path).toHaveAttribute('d', 'M0 0L20 20');
});

test('unknown-case: an unknown id is reported, not rendered as an empty page', async ({page, open}) => {
  await open('no-such-case');
  await expect(page.locator('#app')).toHaveAttribute('data-bench', 'unknown');
  await expect(page.locator('#app')).toHaveText('unknown case: no-such-case');
});
