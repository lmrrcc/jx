import { test, expect } from '@playwright/test';

async function login(page, username) {
  expect((await page.goto('/api/session/login')).status()).toBe(200);
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill('test-only-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByTestId('session-user')).toHaveText(username);
}

test('native session forms, Java CSRF requests and two private workspaces survive the configured hosting mode', async ({ page, browser, baseURL, playwright }, info) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  expect((await page.request.get('/api/todos')).status()).toBe(401);
  expect((await page.goto('/api/session/login')).status()).toBe(200);
  const before = (await page.context().cookies()).find(cookie => cookie.name === 'JXSESSION');
  expect(before).toMatchObject({ path: '/', httpOnly: true, sameSite: 'Lax', secure: false });
  const anonymousToken = await page.locator('input[name=_csrf]').inputValue();
  await page.getByLabel('Username').fill('demo');
  await page.getByLabel('Password').fill('wrong-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('Sign in failed.');
  expect((await page.request.get('/api/todos')).status()).toBe(401);
  await login(page, 'demo');
  const cookie = (await page.context().cookies()).find(cookie => cookie.name === 'JXSESSION');
  expect(cookie).toMatchObject({ path: '/', httpOnly: true, sameSite: 'Lax', secure: false });
  expect(cookie.value).not.toBe(before.value);
  expect(await page.evaluate(() => document.cookie)).not.toContain('JXSESSION');
  const bundle = await (await page.request.get('/application.js')).text();
  expect(bundle).not.toContain('test-only-password');
  expect(bundle).not.toContain('jx.demo.password-hash');
  expect(bundle).not.toContain('jdbc:h2:');
  const sessionResponse = await page.request.get('/api/session');
  expect(sessionResponse.headers()['cache-control']).toContain('no-store');
  const session = await sessionResponse.json();
  expect(session.username).toBe('demo');
  const title = `Private task ${info.project.name}`;
  await page.getByLabel('What needs doing?').fill(title);
  await page.getByLabel('What needs doing?').press('Enter');
  await expect(page.getByRole('listitem').filter({ hasText: title })).toBeVisible();
  const created = (await (await page.request.get('/api/todos')).json()).find(todo => todo.title === title);
  expect(created).toBeTruthy();
  for (const headers of [{ Origin: baseURL }, { Origin: baseURL, 'X-CSRF-TOKEN': anonymousToken },
    { Origin: 'http://foreign.invalid' }]) {
    expect((await page.request.post('/api/todos', { headers, data: { title: 'Rejected mutation' } })).status()).toBe(403);
  }
  expect((await (await page.request.get('/api/todos')).json()).some(todo => todo.title === 'Rejected mutation')).toBe(false);
  const other = await browser.newContext({ baseURL });
  try {
    const otherPage = await other.newPage();
    await login(otherPage, 'other');
    expect((await (await other.request.get('/api/todos')).json()).some(todo => todo.id === created.id)).toBe(false);
    const otherToken = (await (await other.request.get('/api/session')).json()).csrfToken;
    const headers = { Origin: baseURL, 'X-CSRF-TOKEN': otherToken };
    expect((await other.request.put(`/api/todos/${created.id}`, { headers,
      data: { title: 'Stolen', completed: true, version: created.version } })).status()).toBe(409);
    expect((await other.request.delete(`/api/todos/${created.id}?version=${created.version}`, { headers })).status()).toBe(409);
    expect((await (await page.request.get('/api/todos')).json()).find(todo => todo.id === created.id)).toEqual(created);
  } finally { await other.close(); }
  const staleSession = await playwright.request.newContext({ baseURL, storageState: await page.context().storageState() });
  try {
    await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible();
    await expect(page.getByText(title, { exact: true })).toHaveCount(0);
    expect((await page.request.get('/api/todos')).status()).toBe(401);
    expect((await staleSession.get('/api/todos')).status()).toBe(401);
    expect((await staleSession.post('/api/todos', { headers: { Origin: baseURL, 'X-CSRF-TOKEN': session.csrfToken },
      data: { title: 'Logged out' } })).status()).toBe(403);
  } finally { await staleSession.dispose(); }
  expect(errors).toEqual([]);
});
