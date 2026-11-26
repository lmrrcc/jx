import { test, expect } from '../../../tests/packaged/auth-test.js';

test('Java/.jx todo application: validation, persistence, routes, keyboard, loading and errors', async ({ page, request }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/todos');
  await expect(page.getByRole('heading', { name: 'One thing at a time.' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'A clear desk' })).toBeVisible();
  await page.getByRole('button', { name: 'Add task' }).click();
  await expect(page.getByText('Enter a title between 1 and 200 characters.')).toBeVisible();
  await page.getByLabel('What needs doing?').fill('Ship a Java browser app');
  await page.getByLabel('What needs doing?').press('Enter');
  const row = page.getByRole('listitem').filter({ hasText: 'Ship a Java browser app' });
  await expect(row).toBeVisible();
  await expect(page.getByLabel('What needs doing?')).toHaveValue('');
  await expect(page.getByLabel('What needs doing?')).toBeFocused();
  const persisted = await (await request.get('/api/todos')).json();
  expect(persisted).toHaveLength(1);
  expect(persisted[0].title).toBe('Ship a Java browser app');
  // The checkbox is controlled and only becomes checked once the server confirms, so click
  // and wait; check() would demand an immediate change.
  await row.getByRole('checkbox').click();
  await expect(row.getByRole('checkbox')).toBeChecked();
  await expect(row.getByRole('checkbox')).toBeEnabled();
  await page.getByRole('link', { name: 'Active', exact: true }).click();
  await expect(page).toHaveURL(/filter=active/);
  await expect(page.getByRole('heading', { name: 'A clear desk' })).toBeVisible();
  await page.getByRole('link', { name: 'Done', exact: true }).click();
  await row.getByRole('link').click();
  await expect(page).toHaveURL(new RegExp('/todos/' + persisted[0].id));
  await expect(page.getByRole('heading', { name: 'Ship a Java browser app' })).toBeVisible();
  await page.goBack();
  await expect(page).toHaveURL(/filter=done/);
  await expect(row).toBeVisible();
  await page.goForward();
  await expect(page.getByRole('button', { name: 'Mark active' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Ship a Java browser app' })).toBeVisible();
  await page.getByRole('link', { name: 'About', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Small steps. Clear days.' })).toBeFocused();
  await page.getByRole('link', { name: 'Workspace', exact: true }).click();
  await page.getByRole('button', { name: 'Delete Ship a Java browser app' }).click();
  await expect(page.getByRole('heading', { name: 'A clear desk' })).toBeVisible();

  let release;
  const blocked = new Promise(resolve => release = resolve);
  await page.route('**/api/todos', async route => { await blocked; await route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ status: 503, detail: 'Please try again shortly' }) }); });
  await page.reload();
  await expect(page.getByRole('status')).toHaveText('Loading tasks…');
  release();
  await expect(page.getByRole('alert').filter({ hasText: 'Please try again shortly' })).toBeVisible();
  await page.unroute('**/api/todos');
  await page.getByRole('button', { name: 'Retry loading' }).click();
  await expect(page.getByRole('heading', { name: 'A clear desk' })).toBeVisible();
  expect(errors).toEqual([]);
});

test('server rejects invalid fields and stale versions without losing a successful update', async ({ request }) => {
  const invalid = await request.post('/api/todos', { data: { title: ' ' } });
  expect(invalid.status()).toBe(400);
  expect((await invalid.json()).fieldErrors.title).toBeTruthy();
  const created = await (await request.post('/api/todos', { data: { title: 'Versioned task' } })).json();
  const first = await request.put(`/api/todos/${created.id}`, { data: { title: 'Versioned task', completed: true, version: 0 } });
  expect(first.status()).toBe(200);
  const stale = await request.put(`/api/todos/${created.id}`, { data: { title: 'Lost update', completed: false, version: 0 } });
  expect(stale.status()).toBe(409);
  const todos = await (await request.get('/api/todos')).json();
  expect(todos.find(todo => todo.id === created.id)).toMatchObject({ title: 'Versioned task', completed: true, version: 1 });
  await request.delete(`/api/todos/${created.id}?version=1`);
});

test('detail routes recover from unexpected error bodies and mutation conflicts', async ({ page, request }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const todo = await (await request.post('/api/todos', { data: { title: 'Recover this task' } })).json();
  for (const body of [[], 'unavailable', { detail: null }, { detail: '' }]) {
    await page.route('**/api/todos', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify(body) }));
    await page.goto(`/todos/${todo.id}`);
    await expect(page.getByRole('alert')).toContainText('HTTP 503');
    await expect(page.getByRole('heading', { name: 'Task not found' })).toHaveCount(0);
    await page.unroute('**/api/todos');
    await page.getByRole('button', { name: 'Retry loading' }).click();
    await expect(page.getByRole('heading', { name: 'Recover this task' })).toBeVisible();
  }
  await page.route(`**/api/todos/${todo.id}`, route => route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ detail: 'Changed elsewhere; reload' }) }));
  await page.getByRole('button', { name: 'Mark done', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Changed elsewhere; reload');
  await page.unroute(`**/api/todos/${todo.id}`);
  await page.getByRole('button', { name: 'Retry loading' }).click();
  await expect(page.getByRole('button', { name: 'Mark done', exact: true })).toBeVisible();
  expect(errors).toEqual([]);
  await request.delete(`/api/todos/${todo.id}?version=0`);
});
