import {test, expect, log, serverOf, sideOf} from './bench.js';

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

test('form-status: a child of the form sees the pending submission', async ({page, open}) => {
  await open('form-status');
  await page.getByRole('button', {name: 'Send'}).click();
  await expect(page.getByRole('button', {name: 'Sending'})).toBeDisabled();
  // The submission stays pending until the action is released, not only for an instant.
  await page.waitForTimeout(250);
  await expect(page.getByRole('button', {name: 'Sending'})).toBeDisabled();
  await page.getByRole('button', {name: 'Release'}).click();
  await expect(page.getByRole('button', {name: 'Send', exact: true})).toBeEnabled();
});

test('form-actions: function actions on a form and on a submit button receive form data', async ({page, open}) => {
  await open('form-actions');
  await page.getByRole('button', {name: 'Save', exact: true}).click();
  await expect(page.getByRole('status')).toHaveText('form Ada');
  await page.getByLabel('Name').fill('Grace');
  await page.getByRole('button', {name: 'Save differently'}).click();
  await expect(page.getByRole('status')).toHaveText('alternative Grace');
});

test('form-reset: requestFormReset restores uncontrolled fields to their defaults', async ({page, open}) => {
  await open('form-reset');
  await page.getByLabel('Note').fill('edited');
  await page.getByRole('button', {name: 'Reset form'}).click();
  await expect(page.getByLabel('Note')).toHaveValue('start');
});

test('raw-html: explicitly supplied HTML is inserted as markup', async ({page, open}) => {
  await open('raw-html');
  const raw = page.getByTestId('raw');
  expect(await raw.evaluate(node => [...node.children].map(child => child.tagName))).toEqual(['B', 'I']);
  await expect(raw).toHaveText('bold and italic');
});

test('resource-hints: the six resource functions add their elements to the head', async ({page, open}) => {
  await open('resource-hints');
  const head = selector => page.locator('head ' + selector);
  await expect(head('link[rel="preconnect"][href="https://connect.example.test"]')).toHaveCount(1);
  await expect(head('link[rel="dns-prefetch"][href="https://dns.example.test"]')).toHaveCount(1);
  await expect(head('link[rel="preload"][as="style"]')).toHaveCount(1);
  await expect(head('link[rel="modulepreload"]')).toHaveCount(1);
  await expect(head('script[async][src^="data:text/javascript,globalThis.__benchPreinit=1"]')).toHaveCount(1);
  await expect(head('script[type="module"][src^="data:text/javascript,globalThis.__benchPreinitModule"]')).toHaveCount(1);
  await expect.poll(() => page.evaluate(() => [globalThis.__benchPreinit, globalThis.__benchPreinitModule])).toEqual([1, 1]);
});

test('form-state: the deprecated useFormState name keeps state across submissions', async ({page, open}) => {
  // React reports the deprecated name in the console; the behavior is what is compared.
  await open('form-state', {expectErrors: true});
  await expect(page.getByRole('status')).toHaveText('10');
  await page.getByRole('button', {name: 'Add'}).click();
  await expect(page.getByRole('status')).toHaveText('13');
  await page.getByLabel('Step').fill('7');
  await page.getByRole('button', {name: 'Add'}).click();
  await expect(page.getByRole('status')).toHaveText('20');
});

test('root-options: the root reports caught, recoverable and uncaught errors and prefixes ids', async ({page, open}) => {
  await open('root-options', {expectErrors: true});
  expect(await page.getByTestId('id').textContent()).toContain('bench-');
  await page.getByRole('button', {name: 'Explode child'}).click();
  await expect(page.getByRole('alert')).toHaveText('failed');
  await expect.poll(() => log(page)).toEqual(['caught child exploded with stack']);
  await page.getByRole('button', {name: 'Arm'}).click();
  await expect(page.getByTestId('flaky')).toHaveText('recovered');
  await expect.poll(() => log(page)).toEqual(['caught child exploded with stack', 'recoverable flaky once']);
  await page.getByRole('button', {name: 'Break root'}).click();
  await expect(page.locator('#app')).toBeEmpty();
  await expect.poll(async () => (await log(page)).at(-1)).toEqual('uncaught root exploded');
});

test('hydrate-root: hydration attaches to server markup without replacing its nodes', async ({page, open}) => {
  await open('hydrate-root');
  const button = page.getByRole('button', {name: 'Increment'});
  await button.click();
  await expect(page.getByRole('status')).toHaveText('1');
  // The marker was put on the server-rendered node before hydration.
  expect(await button.evaluate(node => node.benchServerNode)).toBe(true);
});

test('hydrate-server: the browser hydrates HTML rendered in Node, keeping nodes and ids', async ({page, open}, info) => {
  const html = await (await serverOf(info)).renderHydratable();
  expect(html).toContain('<p data-testid="origin">server</p>');
  const id = /<input id="([^"]+)"/.exec(html)[1];
  await page.addInitScript(markup => { globalThis.__benchServerHtml = markup; }, html);
  // Any hydration mismatch is a console error, which fails the test.
  await open('hydrate-server');
  // After hydration the store switches from its server snapshot to the browser one.
  await expect(page.getByTestId('origin')).toHaveText('client');
  expect(await page.getByLabel('Name').getAttribute('id')).toEqual(id);
  const button = page.getByRole('button', {name: 'Increment'});
  await button.click();
  await expect(page.getByRole('status')).toHaveText('1');
  expect(await button.evaluate(node => node.benchServerNode)).toBe(true);
});

// The generated table cases compare JX with React directly: the same case is opened on the
// reference side and the two DOM trees must serialize to the same HTML.
async function sameAsReference(page, id, info) {
  const html = await page.locator('#app').innerHTML();
  if (sideOf(info) !== 'jx') return html;
  const reference = await page.context().newPage();
  await reference.goto('../reference/?case=' + id);
  await expect(reference.locator('#app > *').first()).toBeAttached();
  expect(html).toEqual(await reference.locator('#app').innerHTML());
  await reference.close();
  return html;
}

test('dom-elements: every HTML and SVG element renders as in React', async ({page, open}, info) => {
  await open('dom-elements');
  await sameAsReference(page, 'dom-elements', info);
  // Elements are created in the right namespace.
  expect(await page.locator('section[data-group="html"] *').count()).toBeGreaterThan(90);
  const svg = await page.locator('svg[data-group="svg"] > *').evaluateAll(nodes => nodes.map(node => node.namespaceURI));
  expect(svg.length).toBeGreaterThan(55);
  expect(new Set(svg)).toEqual(new Set(['http://www.w3.org/2000/svg']));
});

test('dom-attributes: every attribute name React knows renders as in React', async ({page, open}, info) => {
  await open('dom-attributes');
  const html = await sameAsReference(page, 'dom-attributes', info);
  expect(await page.locator('[data-group="html-attributes"] > div').count()).toBeGreaterThan(380);
  // A few names whose DOM spelling differs from the React one.
  for (const attribute of ['class="2"', 'for="2"', 'accept-charset="2"', 'http-equiv="2"', 'xlink:href="2"', 'viewBox="2"', 'stroke-width="2"'])
    expect(html).toContain(attribute);
});

// Dispatches one native event per handler element and returns what the handlers logged.
const fireAll = page => page.evaluate(() => {
  globalThis.__benchLog = [];
  const native = {doubleclick: 'dblclick', focus: 'focusin', blur: 'focusout'};
  for (const node of document.querySelectorAll('[data-event]')) {
    const name = node.dataset.event.toLowerCase();
    node.dispatchEvent(new Event(native[name] || name, {bubbles: true}));
  }
  return globalThis.__benchLog;
});

test('dom-events: every event handler name React knows is called as in React', async ({page, open}, info) => {
  await open('dom-events');
  const calls = await fireAll(page);
  // Most handlers answer a plain synthetic event; the exact set is whatever React does.
  expect(calls.length).toBeGreaterThan(150);
  for (const call of ['Click', 'Click capture', 'DoubleClick', 'Play', 'Play capture', 'PointerDown', 'Wheel', 'Submit', 'Focus', 'Blur'])
    expect(calls).toContain(call);
  if (sideOf(info) === 'jx') {
    const reference = await page.context().newPage();
    await reference.goto('../reference/?case=dom-events');
    await expect(reference.locator('#app > *').first()).toBeAttached();
    expect(calls).toEqual(await fireAll(reference));
    await reference.close();
  }
});
