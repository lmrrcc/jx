import {test, expect} from '@playwright/test';

test('file-repository application owns independent state and runs without networking', async ({page, context}) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  await page.goto('/');
  await expect(page.getByRole('heading', {name: 'Built outside the JX checkout'})).toBeVisible();
  const first = page.getByRole('region', {name: 'First'});
  const second = page.getByRole('region', {name: 'Second'});
  await expect(first.locator('output')).toHaveText('2');
  await expect(second.locator('output')).toHaveText('10');
  const node = await first.locator('output').elementHandle();
  await first.getByRole('button', {name: 'Increment', exact: true}).click();
  await expect(first.locator('output')).toHaveText('3');
  await expect(second.locator('output')).toHaveText('10');
  await context.setOffline(true);
  await first.getByRole('button', {name: 'Add three'}).click();
  await second.getByRole('button', {name: 'Increment', exact: true}).focus();
  await page.keyboard.press('Enter');
  await expect(first.locator('output')).toHaveText('6');
  await expect(second.locator('output')).toHaveText('11');
  expect(await node.evaluate(element => element === document.querySelector('section output'))).toBe(true);
  expect(errors).toEqual([]);
});
