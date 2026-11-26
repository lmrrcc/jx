import { test as base, expect } from '@playwright/test';

// Real HTTP login for pre-existing application/packaging scenarios. The separate
// authentication suite exercises the actual HTML form and denial paths.
export const test = base.extend({
  request: async ({ playwright, baseURL }, use) => {
    const login = await playwright.request.newContext({ baseURL });
    const token = (await (await login.get('/api/session')).json()).csrfToken;
    const response = await login.post('/api/session/login', {
      form: { username: 'demo', password: 'test-only-password', _csrf: token }, maxRedirects: 0,
    });
    expect(response.status()).toBe(303);
    const fresh = (await (await login.get('/api/session')).json()).csrfToken;
    const request = await playwright.request.newContext({ baseURL, storageState: await login.storageState(),
      extraHTTPHeaders: { 'X-CSRF-TOKEN': fresh } });
    await login.dispose();
    try { await use(request); } finally { await request.dispose(); }
  },
  page: async ({ page, request }, use) => {
    await page.context().addCookies((await request.storageState()).cookies);
    await use(page);
  },
});
export { expect };
