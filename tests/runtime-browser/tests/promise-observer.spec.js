import {test, expect} from '@playwright/test';

test('a throwing Java Promise observer reports once while React resumes the same native thenable', async ({page}) => {
  const pageErrors = [];
  const consoleErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  await page.goto('/?fixture=promise-observer');
  const fallback = page.locator('#promise-observer-fallback');
  const result = page.locator('#promise-observer-result');
  const resolve = page.locator('#promise-observer-resolve');
  const pulse = page.locator('#promise-observer-pulse');
  const pulseCount = page.locator('#promise-observer-pulse-count');

  // The committed fallback proves READER already called Hooks.use on this Promise.
  // Its cached native thenable is subscribed after the intentionally throwing observer.
  await expect(fallback).toHaveText('Waiting for observed promise');
  await expect(result).toHaveCount(0);
  await expect(pulseCount).toHaveText('0');
  expect(pageErrors).toEqual([]);
  expect(consoleErrors).toEqual([]);

  await resolve.click();
  await expect.poll(() => pageErrors.length).toBe(1);
  // TeaVM may prefix the Java exception type, but exactly this one error is permitted.
  expect(pageErrors[0]).toMatch(/^(?:java\.lang\.IllegalStateException: )?promise observer failed$/);
  const expectedErrors = [...pageErrors];

  // React must resume from its native thenable's settlement without an unrelated
  // parent update. A pulse before these assertions could conceal a missing retry.
  await expect(result).toHaveText('resolved value');
  await expect(fallback).toHaveCount(0);
  await expect(pulseCount).toHaveText('0');

  // An ordinary rerender reuses the same Java Promise and cached native thenable.
  // It must not be necessary to replace either identity to escape the fallback.
  await pulse.click();
  await expect(pulseCount).toHaveText('1');
  await expect(result).toHaveText('resolved value');
  await expect(fallback).toHaveCount(0);

  // Repeated settlement must not invoke either observer again or repeat the error.
  await resolve.click();
  await pulse.click();
  await expect(pulseCount).toHaveText('2');
  await expect(result).toHaveText('resolved value');
  expect(pageErrors).toEqual(expectedErrors);
  expect(consoleErrors).toEqual([]);
});
