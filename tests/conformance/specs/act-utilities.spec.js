import {test, expect} from './bench.js';

test('act-utilities: awaited scopes flush async updates and cascading effects, nest, reject and recover', async ({page, open}) => {
  await page.addInitScript(() => { globalThis.IS_REACT_ACT_ENVIRONMENT = true; });
  await open('act-utilities');
  expect(await page.evaluate(() => globalThis.__benchUtilities.outside)).toBeNull();
  let mounted = false;
  try {
    const first = await page.evaluate(() => globalThis.__benchUtilities.mount());
    mounted = true;
    expect(first).toEqual({value: 'mounted', count: '0', log: ['effect:0']});
    await page.evaluate(() => {
      globalThis.__actSettled = false;
      globalThis.__pendingAct = globalThis.__benchUtilities.start().then(result => {
        globalThis.__actSettled = true;
        return result;
      });
    });
    expect(await page.evaluate(() => globalThis.__actSettled)).toBe(false);
    await expect(page.locator('#act-count')).toHaveText('0');
    const next = await page.evaluate(async () => {
      globalThis.__benchUtilities.release();
      return await globalThis.__pendingAct;
    });
    // These snapshots were taken when act resolved, so eventual DOM polling cannot
    // conceal a wrapper that completes before React flushes the second effect update.
    expect(next).toEqual({value: 'arrived', count: '2',
      log: ['effect:0', 'cleanup:0', 'effect:1', 'cleanup:1', 'effect:2']});
    for (const [operation, message] of [['fail', 'act rejected'], ['throwing', 'act threw']]) {
      const failure = await page.evaluate(async operation => {
        try { await globalThis.__benchUtilities[operation](); return 'fulfilled'; }
        catch (error) { return error.message; }
      }, operation);
      expect(failure).toContain(message);
    }
    const nested = await page.evaluate(() => globalThis.__benchUtilities.nested());
    expect(nested.value).toBe('nested');
    expect(nested.count).toBe('3');
    expect(nested.log.slice(-2)).toEqual(['cleanup:2', 'effect:3']);
    expect(await page.evaluate(() => globalThis.__benchUtilities.calls())).toBe(2);
    const synchronous = await page.evaluate(async () => {
      const result = globalThis.__benchUtilities.sync();
      return {immediate: result.immediate, completed: await result.completion};
    });
    expect(synchronous.immediate.count).toBe('4');
    expect(synchronous.immediate.log.slice(-2)).toEqual(['cleanup:3', 'effect:4']);
    expect(synchronous.completed).toEqual({...synchronous.immediate, value: 'sync'});
    expect(await page.evaluate(() => globalThis.__benchUtilities.calls())).toBe(3);
    const syncError = await page.evaluate(async () => {
      try { await globalThis.__benchUtilities.syncThrowing(); return 'fulfilled'; }
      catch (error) { return error.message; }
    });
    expect(syncError).toContain('sync act threw');
    const last = await page.evaluate(() => globalThis.__benchUtilities.unmount());
    mounted = false;
    expect(last.count).toBeNull();
    expect(last.log.at(-1)).toBe('cleanup:4');
  } finally {
    if (mounted) await page.evaluate(() => globalThis.__benchUtilities.unmount());
    await page.evaluate(() => { delete globalThis.IS_REACT_ACT_ENVIRONMENT; });
  }
});
