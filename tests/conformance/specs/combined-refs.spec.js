import {test, expect, log, interaction} from './bench.js';

test('combined-keyed-refs: keyed callback refs retain nodes, track dependencies and balance Strict Mode cleanup', async ({page, open}) => {
  await open('combined-keyed-refs');
  const input = id => page.getByRole('textbox', {name: 'Ref ' + id, exact: true});
  const order = () => page.locator('li[data-row]').evaluateAll(rows => rows.map(node => node.dataset.row));
  const lifecycle = async id => (await log(page)).filter(entry => entry.split(' ')[1] === id);
  const refs = async id => (await lifecycle(id)).filter(entry => entry.startsWith('attach ') || entry.startsWith('detach '));
  await expect.poll(() => refs('b')).toEqual(['attach b 0 INPUT', 'detach b 0', 'attach b 0 INPUT']);
  const originalA = await input('a').elementHandle();
  const originalB = await input('b').elementHandle();
  expect(originalA).not.toBeNull();
  expect(originalB).not.toBeNull();
  try {
    await interaction('strict-callback-ref', async () => {
      for (const id of ['a', 'b']) {
        expect(await refs(id)).toEqual([`attach ${id} 0 INPUT`, `detach ${id} 0`, `attach ${id} 0 INPUT`]);
      }
      await interaction('layout-callback-ref', async () => {
        for (const id of ['a', 'b']) {
          const entries = await lifecycle(id);
          const expected = `layout ${id} 0 INPUT ${id}-initial`;
          expect(entries.filter(entry => entry.startsWith('layout '))).toEqual([expected, expected]);
          expect(entries.indexOf(`attach ${id} 0 INPUT`)).toBeLessThan(entries.indexOf(expected));
          expect(entries.lastIndexOf(`attach ${id} 0 INPUT`)).toBeLessThan(entries.lastIndexOf(expected));
        }
      });
      await input('a').fill('draft a');
      await input('b').fill('draft b');

      await interaction('callback-ref-identity', async () => {
        const before = await log(page);
        await page.getByRole('button', {name: 'Rerender parent', exact: true}).click();
        await expect(page.getByTestId('parent-tick')).toHaveText('1');
        expect(await log(page)).toEqual(before);
        await page.getByRole('button', {name: 'Replace callbacks', exact: true}).click();
        await expect(page.getByTestId('callback-revision')).toHaveText('1');
        for (const id of ['a', 'b']) {
          expect((await refs(id)).slice(-2)).toEqual([`detach ${id} 0`, `attach ${id} 1 INPUT`]);
          const entries = await lifecycle(id);
          const expected = `layout ${id} 1 INPUT draft ${id}`;
          expect(entries).toContain(expected);
          expect(entries.indexOf(`attach ${id} 1 INPUT`)).toBeLessThan(entries.indexOf(expected));
        }
        expect(await input('a').evaluate((node, old) => node === old, originalA)).toBe(true);
        expect(await input('b').evaluate((node, old) => node === old, originalB)).toBe(true);
        await expect(input('a')).toHaveValue('draft a');
      });

      await interaction('key-callback-ref', async () => {
        const beforeA = await refs('a');
        const beforeB = await refs('b');
        await page.getByRole('button', {name: 'Reverse rows', exact: true}).click();
        await expect.poll(order).toEqual(['b', 'a']);
        expect(await refs('a')).toEqual(beforeA);
        expect(await refs('b')).toEqual(beforeB);
        expect(await input('a').evaluate((node, old) => node === old, originalA)).toBe(true);
        expect(await input('b').evaluate((node, old) => node === old, originalB)).toBe(true);
        await page.getByRole('button', {name: 'Remove a', exact: true}).click();
        await expect.poll(order).toEqual(['b']);
        expect(await refs('a')).toEqual([...beforeA, 'detach a 1']);
        expect(await refs('b')).toEqual(beforeB);
        expect(await originalA.evaluate(node => node.isConnected)).toBe(false);
        await page.getByRole('button', {name: 'Restore a', exact: true}).click();
        await expect.poll(order).toEqual(['b', 'a']);
        await expect.poll(async () => (await refs('a')).slice(-3)).toEqual(['attach a 1 INPUT', 'detach a 1', 'attach a 1 INPUT']);
        expect((await lifecycle('a')).filter(entry => entry.startsWith('layout ')).slice(-2))
          .toEqual(['layout a 1 INPUT a-initial', 'layout a 1 INPUT a-initial']);
        expect(await input('a').evaluate((node, old) => node === old, originalA)).toBe(false);
        expect(await input('b').evaluate((node, old) => node === old, originalB)).toBe(true);
        await expect(input('a')).toHaveValue('a-initial');
        await expect(input('b')).toHaveValue('draft b');
        expect(await refs('b')).toEqual(beforeB);
      });

      await page.getByRole('button', {name: 'Clear rows', exact: true}).click();
      await expect(page.locator('li[data-row]')).toHaveCount(0);
      for (const id of ['a', 'b']) {
        const entries = await refs(id);
        expect(entries.at(-1)).toEqual(`detach ${id} 1`);
        expect(entries.filter(entry => entry.startsWith('attach ')).length)
          .toEqual(entries.filter(entry => entry.startsWith('detach ')).length);
      }
      expect(await originalB.evaluate(node => node.isConnected)).toBe(false);
    });
  } finally {
    await originalA.dispose();
    await originalB.dispose();
  }
});

test('combined-imperative-handles: keyed handles follow fresh props and ref ownership without replacing the input', async ({page, open}) => {
  await open('combined-imperative-handles');
  const input = id => page.getByRole('textbox', {name: 'Handle ' + id, exact: true});
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const order = () => page.locator('li[data-row]').evaluateAll(rows => rows.map(node => node.dataset.row));
  const inspect = async (primary, alternate, second, sameA, sameB) => {
    await click('Inspect handles');
    await expect(page.getByTestId('handle-snapshot')).toHaveText(
      `primary=${primary};alternate=${alternate};b=${second};same-a=${sameA};same-b=${sameB}`);
  };
  const originalA = await input('a').elementHandle();
  const originalB = await input('b').elementHandle();
  expect(originalA).not.toBeNull();
  expect(originalB).not.toBeNull();
  let restoredA;
  try {
    await click('Fill a');
    await expect(input('a')).toHaveValue('old:written-a');
    await expect(input('a')).toBeFocused();
    await click('Fill b');
    await expect(input('b')).toHaveValue('old:written-b');
    await click('Remember handles');

    await interaction('key-imperative-handle', async () => {
      await click('Reverse rows');
      await expect.poll(order).toEqual(['b', 'a']);
      await inspect('a/old/old:written-a', 'null', 'b/old/old:written-b', true, true);
      expect(await input('a').evaluate((node, old) => node === old, originalA)).toBe(true);
      expect(await input('b').evaluate((node, old) => node === old, originalB)).toBe(true);
      await click('Fill a');
      await expect(input('a')).toBeFocused();
      await click('Remove a');
      await expect.poll(order).toEqual(['b']);
      await inspect('null', 'null', 'b/old/old:written-b', false, true);
      expect(await originalA.evaluate(node => node.isConnected)).toBe(false);
      await click('Restore a');
      await expect.poll(order).toEqual(['b', 'a']);
      await inspect('a/old/a-initial', 'null', 'b/old/old:written-b', false, true);
      expect(await input('a').evaluate((node, old) => node === old, originalA)).toBe(false);
      expect(await input('b').evaluate((node, old) => node === old, originalB)).toBe(true);
      restoredA = await input('a').elementHandle();
    });

    await interaction('props-imperative-handle', async () => {
      await click('Remember handles');
      await click('Change prefixes');
      await inspect('a/new/a-initial', 'null', 'b/new/old:written-b', false, false);
      expect(await input('a').evaluate((node, old) => node === old, restoredA)).toBe(true);
      expect(await input('b').evaluate((node, old) => node === old, originalB)).toBe(true);
      await click('Fill b');
      await expect(input('b')).toHaveValue('new:written-b');
      await click('Fill a');
      await expect(input('a')).toHaveValue('new:written-a');
      await expect(input('a')).toBeFocused();
    });

    await interaction('refprop-imperative-handle', async () => {
      await click('Remember handles');
      await click('Switch a ref');
      await inspect('null', 'a/new/new:written-a', 'b/new/new:written-b', false, true);
      expect(await input('a').evaluate((node, old) => node === old, restoredA)).toBe(true);
      await click('Fill a');
      await expect(input('a')).toBeFocused();
      await click('Switch a ref');
      await inspect('a/new/new:written-a', 'null', 'b/new/new:written-b', false, true);
      expect(await input('a').evaluate((node, old) => node === old, restoredA)).toBe(true);
      await click('Remove a');
      await inspect('null', 'null', 'b/new/new:written-b', false, true);
      expect(await restoredA.evaluate(node => node.isConnected)).toBe(false);
      await click('Clear rows');
      await inspect('null', 'null', 'null', false, false);
      expect(await originalB.evaluate(node => node.isConnected)).toBe(false);
    });
  } finally {
    await originalA.dispose();
    await originalB.dispose();
    if (restoredA) await restoredA.dispose();
  }
});
