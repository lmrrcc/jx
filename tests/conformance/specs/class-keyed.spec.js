import {test, expect, interaction, log} from './bench.js';

test('class-keyed: PureComponent keys retain class instances, DOM and state through reorders without rendering equal props', async ({page, open}) => {
  await open('class-keyed');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const a = page.getByTestId('class-a');
  const b = page.getByTestId('class-b');
  const ordered = page.getByTestId('class-order').locator('output');
  const inspect = async () => {
    await click('Inspect class refs');
    await expect(page.getByTestId('class-refs')).toHaveText('A=same;B=same');
  };
  await expect(ordered).toHaveText(['A:0', 'B:0']);
  await expect.poll(() => log(page)).toContain('A:mount');
  await expect.poll(() => log(page)).toContain('B:mount');
  const originalA = await a.elementHandle();
  const originalB = await b.elementHandle();
  expect(originalA).not.toBeNull();
  expect(originalB).not.toBeNull();
  try {
    await interaction(['pure-keyed', 'pure-instance-ref'], async () => {
      await click('Increment A');
      await expect(a).toHaveText('A:1');
      await click('Increment B');
      await expect(b).toHaveText('B:1');
      await click('Increment B');
      await expect(b).toHaveText('B:2');

      let offset = (await log(page)).length;
      await inspect();
      expect((await log(page)).slice(offset)).toEqual([]);
      for (const order of [['B:2', 'A:1'], ['A:1', 'B:2']]) {
        offset = (await log(page)).length;
        await click('Reverse classes');
        // DOM order is the commit barrier; equal fields must still let PureComponent bail out.
        await expect(ordered).toHaveText(order);
        expect(await a.evaluate((node, original) => node === original, originalA)).toBe(true);
        expect(await b.evaluate((node, original) => node === original, originalB)).toBe(true);
        await inspect();
        // Includes inspection's parent rerender: no child render, mount or unmount is permitted.
        expect((await log(page)).slice(offset)).toEqual([]);
        await expect(a).toHaveText('A:1');
        await expect(b).toHaveText('B:2');
      }
    });
  } finally {
    await originalA.dispose();
    await originalB.dispose();
  }
});
