import { test, expect } from '@playwright/test';

test('contract exact values cross actual Spring and Java/TeaVM without numeric coercion', async ({ page, request }) => {
  const errors = [];
  const bodies = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('request', request => {
    if (request.url().endsWith('/api/contract-values')) bodies.push(request.postDataJSON());
  });
  await page.goto('/todos?fixture=contract');
  await expect(page.getByTestId('contract-result')).toContainText('PASS 12');
  const rows = (await page.getByTestId('contract-result').innerText()).split('\n').slice(1);
  expect(rows[0]).toBe('-9223372036854775808|123456789012345678901234567890123456789|9|absent');
  expect(rows.at(-1)).toBe('9223372036854775807|0|2|value');
  // StrictMode may start then cancel its first effect. Every actual outgoing request
  // must nevertheless use the same textual numeric policy, including 0 and 1.
  expect(bodies.length).toBeGreaterThanOrEqual(12);
  for (const body of bodies) {
    expect(typeof body.id).toBe('string');
    expect(typeof body.amount).toBe('string');
    expect(Object.hasOwn(body, 'note')).toBe(true);
  }
  const ids = bodies.map(body => body.id);
  for (const id of ['-9223372036854775808', '-9007199254740993', '-9007199254740992', '-9007199254740991',
    '-1', '0', '1', '42', '9007199254740991', '9007199254740992', '9007199254740993', '9223372036854775807']) {
    expect(ids).toContain(id);
  }
  expect(bodies.some(body => !Object.hasOwn(body, 'extra'))).toBe(true);
  expect(bodies.some(body => Object.hasOwn(body, 'extra') && body.extra === null)).toBe(true);
  expect(bodies.some(body => body.extra === '')).toBe(true);
  for (const body of [
    { id: 1, amount: '1.0', note: null }, { id: '1', amount: 1, note: null },
    { id: '1', amount: '1.0' }, { id: '01', amount: '1.0', note: null },
    { id: '1', amount: '1.0', note: null, extra: false },
  ]) expect((await request.post('/api/contract-values', { data: body })).status()).toBe(400);
  expect(errors).toEqual([]);
});
