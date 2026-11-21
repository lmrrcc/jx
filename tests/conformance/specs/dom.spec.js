import {test, expect, log} from './bench.js';

test('choice-inputs: controlled radio group, select and textarea', async ({page, open}) => {
  await open('choice-inputs');
  const status = page.getByRole('status');
  await expect(status).toHaveText('small|red|first');
  await page.getByLabel('Large').check();
  await expect(page.getByLabel('Small')).not.toBeChecked();
  await page.getByLabel('Color').selectOption('green');
  await page.getByLabel('Notes').fill('typed');
  await expect(status).toHaveText('large|green|typed');
  await page.getByRole('button', {name: 'Reset'}).click();
  await expect(page.getByLabel('Small')).toBeChecked();
  await expect(page.getByLabel('Color')).toHaveValue('blue');
  await expect(page.getByLabel('Notes')).toHaveValue('reset');
});

test('uncontrolled-inputs: defaults seed the DOM once and renders keep user input', async ({page, open}) => {
  await open('uncontrolled-inputs');
  const text = page.getByLabel('Free text');
  const option = page.getByLabel('Option');
  const choice = page.getByLabel('Choice');
  await expect(text).toHaveValue('initial');
  await expect(option).toBeChecked();
  await expect(choice).toHaveValue('b');
  await text.fill('edited');
  await option.uncheck();
  await choice.selectOption('a');
  await page.getByRole('button', {name: 'Rerender'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  await expect(text).toHaveValue('edited');
  await expect(option).not.toBeChecked();
  await expect(choice).toHaveValue('a');
});

test('capture-phase: capture handlers run first, outermost first; bubble handlers after', async ({page, open}) => {
  await open('capture-phase');
  await page.getByRole('button', {name: 'Press'}).click();
  expect(await log(page)).toEqual(['capture outer', 'capture target', 'bubble target', 'bubble outer']);
});

test('custom-elements: hyphenated tags render with attributes and children', async ({page, open}) => {
  await open('custom-elements');
  const badge = page.locator('bench-badge');
  await expect(badge).toHaveText('content');
  await expect(badge).toHaveAttribute('label', 'first');
  await expect(badge).toHaveAttribute('data-kind', 'tag');
  await expect(badge).toHaveAttribute('class', 'badge');
  await page.getByRole('button', {name: 'Change label'}).click();
  await expect(badge).toHaveAttribute('label', 'second');
});

test('document-metadata: title and meta are placed in the head and follow state', async ({page, open}) => {
  await open('document-metadata');
  await expect(page).toHaveTitle('Bench page 1');
  const description = page.locator('head meta[name="description"]');
  await expect(description).toHaveAttribute('content', 'Description 1');
  expect(await page.locator('main title, main meta').count()).toEqual(0);
  await page.getByRole('button', {name: 'Next page'}).click();
  await expect(page).toHaveTitle('Bench page 2');
  await expect(description).toHaveAttribute('content', 'Description 2');
  await expect(description).toHaveCount(1);
});

test('resource-elements: a style with href and precedence is hoisted once', async ({page, open}) => {
  await open('resource-elements');
  await expect(page.getByTestId('styled')).toHaveCSS('color', 'rgb(0, 128, 0)');
  await expect(page.locator('head style[data-href="bench-style"]')).toHaveCount(1);
  expect(await page.locator('main style').count()).toEqual(0);
});

test('error-reporting: handler errors are reported and keep the UI; an uncaught render error unmounts the root', async ({page, open}) => {
  const reports = await open('error-reporting', {expectErrors: true});
  await page.getByRole('button', {name: 'Throw in handler'}).click();
  await expect.poll(() => reports.filter(report => report.includes('handler exploded')).length).toBeGreaterThan(0);
  await expect(page.getByRole('status')).toHaveText('alive');
  await page.getByRole('button', {name: 'Throw in render'}).click();
  await expect.poll(() => reports.filter(report => report.includes('render exploded')).length).toBeGreaterThan(0);
  await expect(page.locator('#app')).toBeEmpty();
});

test('portal: portal content renders elsewhere in the DOM but events reach the React parent', async ({page, open}) => {
  await open('portal');
  const ported = page.getByTestId('ported');
  expect(await ported.evaluate(node => node.parentElement === document.body)).toBe(true);
  expect(await page.getByTestId('owner').evaluate(node => node.querySelector('[data-testid="ported"]'))).toBeNull();
  await ported.click();
  await expect(page.getByRole('status')).toHaveText('1');
});

test('flush-sync: the DOM is updated before flushSync returns', async ({page, open}) => {
  await open('flush-sync');
  await page.getByRole('button', {name: 'Flush'}).click();
  await expect(page.getByTestId('seen')).toHaveText('dom showed 1');
});
