import { test, expect } from './auth-test.js';

test('one executable Boot JAR serves Java UI and API from an empty directory', async ({ page, request }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/todos');
  await expect(page.getByRole('heading', { name: 'One thing at a time.' })).toBeVisible();
  await page.getByLabel('What needs doing?').fill('Packaged Java UI');
  await page.getByLabel('What needs doing?').press('Enter');
  const row = page.getByRole('listitem').filter({ hasText: 'Packaged Java UI' });
  await expect(row).toBeVisible();
  const todos = await (await request.get('/api/todos')).json();
  expect(todos).toHaveLength(1);
  await page.goto(`/todos/${todos[0].id}`);
  await expect(page.getByRole('heading', { name: 'Packaged Java UI' })).toBeVisible();
  await page.getByRole('button', { name: 'Mark done' }).click();
  await expect(page.getByRole('button', { name: 'Mark active' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('button', { name: 'Mark active' })).toBeVisible();
  expect(errors).toEqual([]);
});

test('classpath assets have correct types and unknown API assets or private files never return the shell', async ({ request }) => {
  const script = await request.get('/application.js');
  expect(script.status()).toBe(200);
  expect(script.headers()['content-type']).toMatch(/javascript/);
  const css = await request.get('/styles.css');
  expect(css.status()).toBe(200);
  expect(css.headers()['content-type']).toContain('text/css');
  for (const path of ['/api/missing', '/missing-chunk.js', '/application.js.map', '/application.yml', '/jx/example/api/TodoController.class']) {
    const response = await request.get(path);
    expect(response.status()).toBe(404);
    expect(await response.text()).not.toContain('<div id="root">');
  }
  expect((await request.post('/todos')).status()).toBe(405);
  expect((await request.get('/api/contract-values')).status()).toBe(404);
});
