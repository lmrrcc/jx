import {expect, test} from '@playwright/test';

test('Flight renders server content and resolves an interactive client component', async ({page}, info) => {
  const errors = [];
  const chunks = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('response', response => { if (/\/assets\/client.*\.js$/.test(response.url())) chunks.push(response.url()); });
  const flight = page.waitForResponse(response => response.url().includes('/flight?'));
  await page.goto('/?side=' + info.project.name + '&request=alpha');
  const response = await flight;
  expect(response.status()).toBe(200);
  expect(response.headers()['content-type']).toBe('text/x-component');
  const payload = await response.text();
  expect(payload).toContain('Java Server Component');
  expect(payload).not.toContain('<main>');
  await expect(page.getByRole('heading', {name: 'Java Server Component'})).toBeVisible();
  await expect(page.getByRole('status')).toHaveText('alpha');
  const counter = page.getByRole('button', {name: 'alpha: 5'});
  await counter.click();
  await expect(page.getByRole('button', {name: 'alpha: 6'})).toBeVisible();
  await page.context().setOffline(true);
  await page.getByRole('button', {name: 'alpha: 6'}).click();
  await expect(page.getByRole('button', {name: 'alpha: 7'})).toBeVisible();
  expect(chunks.length).toBeGreaterThan(0);
  expect(await page.evaluate(() => typeof globalThis.__jxRscClient?.Counter === 'function'))
    .toBe(info.project.name === 'jx');
  expect(errors).toEqual([]);
});

test('the proof host rejects malformed input and exposes only owned browser assets', async ({request}) => {
  for (const path of ['/flight?side=unknown&request=alpha', '/flight?side=jx&request=../../secret',
    '/flight?side=jx&request=' + 'a'.repeat(33)]) {
    const response = await request.get(path);
    expect(response.status()).toBe(400);
    expect(await response.text()).toBe('Invalid proof props');
  }
  expect((await request.post('/flight?side=jx&request=alpha')).status()).toBe(400);
  for (const path of ['/target/server.cjs', '/assets/../server.cjs', '/assets/react-client-manifest.json']) {
    expect((await request.get(path)).status()).toBe(404);
  }
});
