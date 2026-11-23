import {test as base, expect} from '@playwright/test';

/**
 * Opens one case alone on a fresh page. Any uncaught page error or console error
 * fails the test, so both implementations must be equally clean. A case that
 * expects error reports passes `{expectErrors: true}`; `open` returns the live list
 * of reports so such a case can assert on them.
 */
export const test = base.extend({
  open: async ({page}, use) => {
    const problems = [];
    let tolerated = false;
    page.on('pageerror', error => problems.push('pageerror: ' + error.message));
    page.on('console', message => { if (message.type() === 'error') problems.push('console: ' + message.text()); });
    await use(async (id, options = {}) => {
      tolerated = options.expectErrors === true;
      await page.goto('./?case=' + encodeURIComponent(id));
      await expect(page.locator('#app > *, #app[data-bench]').first()).toBeAttached();
      return problems;
    });
    if (!tolerated) expect(problems).toEqual([]);
  }
});
export {expect};

/** The page's ordered lifecycle log, written by both implementations. */
export const log = page => page.evaluate(() => globalThis.__benchLog || []);

/**
 * A case for a React feature that JX does not offer yet. The reference must pass.
 * On the JX side the test asserts that the case is still absent, so implementing
 * it forces the test, and the feature list, to be updated.
 */
export function referenceOnly(title, body) {
  test(title, async ({page, open}, info) => {
    if (info.project.name !== 'jx') return body({page, open});
    info.annotations.push({type: 'unsupported'});
    await open(title.split(':')[0]);
    await expect(page.locator('#app')).toHaveAttribute('data-bench', 'unknown');
  });
}

/** A case that runs in Node, without a page, on both sides: React's server APIs in the
 * reference, the JX application started as a Node host on the JX side. */
export function server(title, body) {
  base(title, async ({}, info) => body(await serverOf(info)));
}

/** The server functions of the project a test runs in. */
export async function serverOf(info) {
  const file = info.project.name === 'jx' ? '../target/jx-server/server.cjs' : '../target/reference-site/server.cjs';
  const module = await import(file);
  return (module.default || module).server;
}

/** A reference-only case that runs React's server APIs in Node, without a page. */
export function serverOnly(title, body) {
  base(title, async ({}, info) => {
    if (info.project.name === 'jx') {
      info.annotations.push({type: 'unsupported'});
      return;
    }
    const module = await import('../target/reference-site/server.cjs');
    await body((module.default || module).server);
  });
}
