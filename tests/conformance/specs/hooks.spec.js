import {test, expect, log} from './bench.js';

test('state-counter: render snapshots and queued functional updates', async ({page, open}) => {
  await open('state-counter');
  const count = page.getByRole('status');
  await expect(count).toHaveText('0');
  await page.getByRole('button', {name: 'Increment'}).click();
  await expect(count).toHaveText('1');
  await page.getByRole('button', {name: 'Add three'}).click();
  await expect(count).toHaveText('4');
  // Both calls read the same render snapshot, so only one increment is applied.
  await page.getByRole('button', {name: 'Set from snapshot twice'}).click();
  await expect(count).toHaveText('5');
});

test('state-lazy-init: the initializer supplies the first value and is not rerun on update', async ({page, open}) => {
  await open('state-lazy-init');
  await expect(page.getByRole('status')).toHaveText('7');
  // Strict Mode in development calls the initializer twice.
  expect(await log(page)).toEqual(['init', 'init']);
  await page.getByRole('button', {name: 'Increment'}).click();
  await expect(page.getByRole('status')).toHaveText('8');
  expect(await log(page)).toEqual(['init', 'init']);
});

test('callable-state: a function value is stored, not called as an updater', async ({page, open}) => {
  await open('callable-state');
  await expect(page.getByRole('status')).toHaveText('first');
  await page.getByRole('button', {name: 'Replace function'}).click();
  await expect(page.getByRole('status')).toHaveText('second');
});

test('reducer-batch: two dispatches in one event are reduced in order', async ({page, open}) => {
  await open('reducer-batch');
  await expect(page.getByRole('status')).toHaveText('');
  await page.getByRole('button', {name: 'Dispatch a then b'}).click();
  await expect(page.getByRole('status')).toHaveText('ab');
  await page.getByRole('button', {name: 'Dispatch a then b'}).click();
  await expect(page.getByRole('status')).toHaveText('abab');
});

test('effect-cleanup: setup and cleanup on mount, dependency change and unmount', async ({page, open}) => {
  await open('effect-cleanup');
  // Strict Mode in development mounts, cleans up and mounts effects again.
  await expect.poll(() => log(page)).toEqual(['setup a', 'cleanup a', 'setup a']);
  await page.getByRole('button', {name: 'Rerender only'}).click();
  await expect(page.getByTestId('renders')).toHaveText('1');
  expect(await log(page)).toEqual(['setup a', 'cleanup a', 'setup a']);
  await page.getByRole('button', {name: 'Change dependency'}).click();
  await expect(page.getByRole('status')).toHaveText('b');
  await expect.poll(() => log(page)).toEqual(['setup a', 'cleanup a', 'setup a', 'cleanup a', 'setup b']);
  await page.getByRole('button', {name: 'Toggle probe'}).click();
  await expect(page.getByRole('status')).toHaveCount(0);
  await expect.poll(() => log(page)).toEqual(['setup a', 'cleanup a', 'setup a', 'cleanup a', 'setup b', 'cleanup b']);
});

test('effect-dependencies: no dependency list runs every commit, an empty list runs once', async ({page, open}) => {
  await open('effect-dependencies');
  const mount = ['always', 'once', 'cleanup always', 'cleanup once', 'always', 'once'];
  await expect.poll(() => log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Rerender'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  await expect.poll(() => log(page)).toEqual([...mount, 'cleanup always', 'always']);
});

test('layout-effect-order: layout effects run before passive effects', async ({page, open}) => {
  await open('layout-effect-order');
  await expect.poll(() => log(page)).toEqual(
    ['layout', 'effect', 'cleanup layout', 'cleanup effect', 'layout', 'effect']);
});

test('batched-updates: two state updates in one event produce one commit', async ({page, open}) => {
  await open('batched-updates');
  await expect.poll(() => log(page)).toEqual(['0,0', '0,0']);
  await page.getByRole('button', {name: 'Update both'}).click();
  await expect(page.getByRole('status')).toHaveText('2');
  await expect.poll(() => log(page)).toEqual(['0,0', '0,0', '1,1']);
});

test('ref-persist: a ref keeps its value and does not schedule a render', async ({page, open}) => {
  await open('ref-persist');
  await page.getByRole('button', {name: 'Bump ref'}).click();
  await page.getByRole('button', {name: 'Bump ref'}).click();
  await expect(page.getByRole('status')).toHaveText('0');
  await page.getByRole('button', {name: 'Render'}).click();
  await expect(page.getByRole('status')).toHaveText('2');
});

test('dom-ref-focus: the element is attached before layout effects and can be read', async ({page, open}) => {
  await open('dom-ref-focus');
  const field = page.getByLabel('Field');
  await expect(field).toBeFocused();
  await field.fill('typed');
  await page.getByRole('button', {name: 'Read field'}).click();
  await expect(page.getByRole('status')).toHaveText('typed');
});

test('context-nested: default, provider, nested override and provider update', async ({page, open}) => {
  await open('context-nested');
  const theme = name => page.locator(`[data-theme-of="${name}"]`);
  await expect(theme('outside')).toHaveText('light');
  await expect(theme('inside')).toHaveText('dark');
  await expect(theme('nested')).toHaveText('fixed');
  await page.getByRole('button', {name: 'Change provider'}).click();
  await expect(theme('inside')).toHaveText('contrast');
  await expect(theme('outside')).toHaveText('light');
  await expect(theme('nested')).toHaveText('fixed');
});

test('memo-recompute: recomputed only when the dependency changes', async ({page, open}) => {
  await open('memo-recompute');
  await expect(page.getByRole('status')).toHaveText('2');
  // Strict Mode in development runs the calculation twice per computation.
  expect(await log(page)).toEqual(['compute 1', 'compute 1']);
  await page.getByRole('button', {name: 'Unrelated render'}).click();
  await expect(page.getByTestId('noise')).toHaveText('1');
  expect(await log(page)).toEqual(['compute 1', 'compute 1']);
  await page.getByRole('button', {name: 'Change dependency'}).click();
  await expect(page.getByRole('status')).toHaveText('4');
  expect(await log(page)).toEqual(['compute 1', 'compute 1', 'compute 2', 'compute 2']);
});

test('callback-identity: stable until a dependency changes', async ({page, open}) => {
  await open('callback-identity');
  await page.getByRole('button', {name: 'Unrelated render'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  await expect(page.getByTestId('stable')).toHaveText('same');
  await expect(page.getByTestId('dependent')).toHaveText('same');
  await page.getByRole('button', {name: 'Change dependency'}).click();
  await expect(page.getByTestId('dependent')).toHaveText('changed');
  await expect(page.getByTestId('stable')).toHaveText('same');
});

test('use-id: ids are unique per instance and link label to input', async ({page, open}) => {
  await open('use-id');
  const ids = await page.locator('input').evaluateAll(inputs => inputs.map(input => input.id));
  expect(ids).toHaveLength(2);
  expect(ids[0]).not.toEqual('');
  expect(ids[0]).not.toEqual(ids[1]);
  await page.getByText('Second').click();
  await expect(page.getByLabel('Second')).toBeFocused();
  await page.getByText('First').click();
  await expect(page.getByLabel('First')).toBeFocused();
});

test('external-store: follows a store React does not own and unsubscribes on unmount', async ({page, open}) => {
  await open('external-store');
  await expect(page.getByRole('status')).toHaveText('0');
  await page.getByRole('button', {name: 'Change store'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  const subscribed = await log(page);
  expect(subscribed.at(-1)).toEqual('subscribe');
  await page.getByRole('button', {name: 'Toggle reader'}).click();
  await expect(page.getByRole('status')).toHaveCount(0);
  await expect.poll(() => log(page)).toEqual([...subscribed, 'unsubscribe']);
  await page.getByRole('button', {name: 'Change store'}).click();
  await page.getByRole('button', {name: 'Toggle reader'}).click();
  await expect(page.getByRole('status')).toHaveText('2');
});

test('error-boundary: a render error shows the fallback and siblings survive', async ({page, open}) => {
  await open('error-boundary', {expectErrors: true});
  await page.getByRole('button', {name: 'Explode'}).click();
  await expect(page.getByRole('alert')).toHaveText('failed: render exploded');
  await expect(page.getByTestId('sibling')).toHaveText('sibling');
  await expect(page.getByRole('button', {name: 'Explode'})).toHaveCount(0);
});

test('layout-effect-dependencies: a layout effect reruns only when its dependency changes', async ({page, open}) => {
  await open('layout-effect-dependencies');
  const mount = ['layout 1', 'cleanup 1', 'layout 1'];
  await expect.poll(() => log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Unrelated render'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  expect(await log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Change dependency'}).click();
  await expect.poll(() => log(page)).toEqual([...mount, 'cleanup 1', 'layout 2']);
});

test('create-ref: a ref object created outside a component attaches to an element', async ({page, open}) => {
  await open('create-ref');
  await page.getByRole('button', {name: 'Focus field'}).click();
  await expect(page.getByLabel('Created')).toBeFocused();
});

test('imperative-handle: a component exposes chosen methods instead of its DOM node', async ({page, open}) => {
  await open('imperative-handle');
  await page.getByRole('button', {name: 'Fill through handle'}).click();
  await expect(page.getByLabel('Inner')).toHaveValue('filled');
  await expect(page.getByLabel('Inner')).toBeFocused();
  // The value is read back through the handle, not from the node.
  await expect(page.getByRole('status')).toHaveText('filled');
});

test('forward-ref: a ref passes through a component to its element', async ({page, open}) => {
  await open('forward-ref');
  await page.getByRole('button', {name: 'Focus field'}).click();
  await expect(page.getByLabel('Forwarded')).toBeFocused();
});

test('ref-as-prop: a function component receives ref as an ordinary prop', async ({page, open}) => {
  await open('ref-as-prop');
  await page.getByRole('button', {name: 'Focus field'}).click();
  await expect(page.getByLabel('Direct')).toBeFocused();
});

test('ref-callback: a callback ref gets the node and its cleanup runs on removal', async ({page, open}) => {
  await open('ref-callback');
  // Strict Mode in development attaches, detaches and attaches again.
  await expect.poll(() => log(page)).toEqual(['attach P', 'detach', 'attach P']);
  // A stable callback is not detached and attached again by a rerender.
  await page.getByRole('button', {name: 'Rerender'}).click();
  await expect(page.getByTestId('ticks')).toHaveText('1');
  expect(await log(page)).toEqual(['attach P', 'detach', 'attach P']);
  await page.getByRole('button', {name: 'Remove node'}).click();
  await expect.poll(() => log(page)).toEqual(['attach P', 'detach', 'attach P', 'detach']);
});

test('reducer-lazy-init: the initializer computes the first reducer state once per mount', async ({page, open}) => {
  await open('reducer-lazy-init');
  await expect(page.getByRole('status')).toHaveText('10');
  // Strict Mode in development calls the initializer twice.
  expect(await log(page)).toEqual(['init', 'init']);
  await page.getByRole('button', {name: 'Increment'}).click();
  await expect(page.getByRole('status')).toHaveText('11');
  expect(await log(page)).toEqual(['init', 'init']);
});

test('strict-nested: a nested StrictMode checks its children only', async ({page, open}) => {
  await open('strict-nested');
  // Only the nested part renders twice. React does not repeat the effects of a nested
  // StrictMode that mounts together with a parent outside Strict Mode.
  const mount = ['render plain', 'render checked', 'render checked', 'plain setup', 'checked setup'];
  await expect.poll(() => log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Show late'}).click();
  // A StrictMode subtree that mounts later also repeats its effects.
  await expect.poll(() => log(page)).toEqual([...mount, 'render plain', 'render checked', 'render checked',
    'render late', 'render late', 'late setup', 'late cleanup', 'late setup']);
});

test('insertion-effect: insertion effects run before layout and passive effects', async ({page, open}) => {
  await open('insertion-effect');
  await expect.poll(async () => (await log(page)).slice(0, 3)).toEqual(['insertion', 'layout', 'effect']);
});

test('effect-event: an effect event reads the latest values without re-running the effect', async ({page, open}) => {
  await open('effect-event');
  const mount = ['connected a light', 'disconnected a', 'connected a light'];
  await expect.poll(() => log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Change theme'}).click();
  await expect(page.getByRole('status')).toHaveText('a dark');
  expect(await log(page)).toEqual(mount);
  await page.getByRole('button', {name: 'Change room'}).click();
  await expect.poll(() => log(page)).toEqual([...mount, 'disconnected a', 'connected b dark']);
});

test('debug-value: a hook labelled with useDebugValue behaves like the plain hook', async ({page, open}) => {
  await open('debug-value');
  await page.getByRole('button', {name: 'Increment'}).click();
  await expect(page.getByRole('status')).toHaveText('2');
});

test('profiler: onRender reports the mount and each update', async ({page, open}) => {
  await open('profiler');
  await expect.poll(async () => (await log(page))[0]).toEqual('bench mount');
  await page.getByRole('button', {name: 'Update'}).click();
  await expect(page.getByRole('status')).toHaveText('1');
  await expect.poll(async () => (await log(page)).at(-1)).toEqual('bench update');
});
