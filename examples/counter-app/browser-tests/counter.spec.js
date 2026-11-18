import { test, expect } from '@playwright/test';

test('Maven-compiled JX components retain independent browser state offline', async ({ page, context }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  const first = page.getByRole('region', { name: 'First counter' });
  const second = page.getByRole('region', { name: 'Second counter' });
  await expect(first.getByRole('status')).toHaveText('0');
  await expect(second.getByRole('status')).toHaveText('10');
  await first.getByRole('button', { name: 'Increment First counter', exact: true }).click();
  await expect(first.getByRole('status')).toHaveText('1');
  await page.getByRole('button', { name: 'Rerender parent' }).click();
  await expect(first.getByRole('status')).toHaveText('1');
  await context.setOffline(true);
  await first.getByRole('button', { name: 'Add three to First counter', exact: true }).click();
  await expect(first.getByRole('status')).toHaveText('4');
  await expect(second.getByRole('status')).toHaveText('10');
  await second.getByRole('button', { name: 'Increment Second counter', exact: true }).click();
  await page.getByRole('button', { name: 'Toggle first counter' }).click();
  await expect(first).toHaveCount(0);
  await page.getByRole('button', { name: 'Toggle first counter' }).click();
  await expect(first.getByRole('status')).toHaveText('0');
  await expect(second.getByRole('status')).toHaveText('11');
  expect(errors).toEqual([]);
});
