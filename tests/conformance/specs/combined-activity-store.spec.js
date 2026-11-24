import {test, expect, interaction, log} from './bench.js';

test('combined-activity-store: hiding releases subscriptions and effects while retaining local state and reading the latest store on reveal', async ({page, open}) => {
  await open('combined-activity-store');
  const reader = page.getByRole('region', {name: 'Activity reader'});
  const inspect = async count => {
    await page.getByRole('button', {name: 'Inspect subscriptions'}).click();
    await expect(page.getByTestId('subscriptions')).toHaveText(String(count));
  };
  const effectBalance = async () => (await log(page)).reduce((total, entry) =>
    total + (entry === 'activity mounted' ? 1 : entry === 'activity cleaned' ? -1 : 0), 0);

  await inspect(1);
  await page.getByRole('button', {name: 'Increment local'}).click();
  await expect(page.getByTestId('local-value')).toHaveText('1');
  await interaction('memo-store', async () => {
    await page.getByRole('button', {name: 'Publish store'}).click();
    await expect(page.getByTestId('store-value')).toHaveText('1');
    await expect(page.getByTestId('local-value')).toHaveText('1');
  });

  await interaction(['activity-store', 'activity-state', 'activity-effects'], async () => {
    const original = await reader.elementHandle();
    try {
      await expect.poll(effectBalance).toBe(1);
      await page.getByRole('button', {name: 'Toggle visibility'}).click();
      await expect(page.getByTestId('local-value')).toBeHidden();
      await expect.poll(effectBalance).toBe(0);
      await inspect(0);
      await page.getByRole('button', {name: 'Publish store'}).click();
      await page.getByRole('button', {name: 'Publish store'}).click();
      await inspect(0);

      await page.getByRole('button', {name: 'Toggle visibility'}).click();
      await expect(reader).toBeVisible();
      await expect(page.getByTestId('local-value')).toHaveText('1');
      await expect(page.getByTestId('store-value')).toHaveText('3');
      expect(await original.evaluate(node => node === document.querySelector('[aria-label="Activity reader"]'))).toBe(true);
      await expect.poll(effectBalance).toBe(1);
      await inspect(1);

      await page.getByRole('button', {name: 'Toggle attachment'}).click();
      await expect(reader).toHaveCount(0);
      await expect.poll(effectBalance).toBe(0);
      await inspect(0);
      await page.getByRole('button', {name: 'Publish store'}).click();
      await page.getByRole('button', {name: 'Toggle attachment'}).click();
      await expect(page.getByTestId('local-value')).toHaveText('0');
      await expect(page.getByTestId('store-value')).toHaveText('4');
      await expect.poll(effectBalance).toBe(1);
      await inspect(1);
    } finally {
      await original.dispose();
    }
  });
});
