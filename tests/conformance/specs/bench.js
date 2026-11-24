import {test as base, expect} from '@playwright/test';
import {readFileSync} from 'node:fs';

/**
 * Opens one case alone on a fresh page. Any uncaught page error or console error
 * fails the test, so both implementations must be equally clean. A case that
 * expects error reports passes `{expectErrors: true}`; `open` returns the live list
 * of reports so such a case can assert on them.
 */
/** 'reference' or 'jx': the implementation a test runs against. */
export const sideOf = info => info.project.name.split('-')[0];
/** 'client', 'hydrate' or 'static': the rendering mode a test runs in. */
export const modeOf = info => info.project.name.includes('-') ? info.project.name.split('-')[1] : 'client';

// Cases that do not apply to a mode, with the reason: tests/conformance/modes.tsv.
const notApplicable = new Map(readFileSync(new URL('../modes.tsv', import.meta.url), 'utf8').split('\n').slice(1)
  .filter(Boolean).map(line => line.split('\t')).map(([id, mode, reason]) => [id + ' ' + mode, reason]));

const extended = base.extend({
  open: async ({page}, use, info) => {
    const problems = [];
    let tolerated = false;
    page.on('pageerror', error => problems.push('pageerror: ' + error.message));
    page.on('console', message => { if (message.type() === 'error') problems.push('console: ' + message.text()); });
    await use(async (id, options = {}) => {
      tolerated = options.expectErrors === true;
      let query = './?case=' + encodeURIComponent(id);
      if (modeOf(info) === 'hydrate') {
        // The same implementation renders the case in Node; the page hydrates that markup.
        const html = await (await serverOf(info)).renderCase(id);
        await page.addInitScript(markup => { globalThis.__benchServerHtml = markup; }, html);
        query += '&mode=hydrate';
      }
      await page.goto(query);
      await expect(page.locator('#app > *, #app[data-bench]').first()).toBeAttached();
      return problems;
    });
    if (!tolerated) expect(problems).toEqual([]);
  }
});

/** One case, run in every project. A case listed in modes.tsv is skipped in that mode. */
export function test(title, body) {
  extended(title, async ({page, open}, info) => {
    const reason = notApplicable.get(title.split(':')[0] + ' ' + modeOf(info));
    extended.skip(reason !== undefined, reason);
    if (modeOf(info) === 'static') {
      // Static mode has no script, so the spec cannot run. The case is rendered to static
      // HTML in Node, and JX must produce exactly the markup React produces.
      const id = title.split(':')[0];
      const html = await (await serverOf(info)).renderStatic(id);
      expect(html.length).toBeGreaterThan(0);
      if (sideOf(info) === 'jx') expect(html).toEqual(await (await serverOf({project: {name: 'reference'}})).renderStatic(id));
      return;
    }
    await body({page, open}, info);
  });
}
test.skip = extended.skip;
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
    if (sideOf(info) !== 'jx') return body({page, open});
    info.annotations.push({type: 'unsupported'});
    await open(title.split(':')[0]);
    await expect(page.locator('#app')).toHaveAttribute('data-bench', 'unknown');
  });
}

/** A case that runs in Node, without a page, on both sides: React's server APIs in the
 * reference, the JX application started as a Node host on the JX side. */
export function server(title, body) {
  base(title, async ({}, info) => {
    base.skip(modeOf(info) !== 'client', 'a server case has no page to render in another mode');
    await body(await serverOf(info));
  });
}

/** The server functions of the project a test runs in. */
export async function serverOf(info) {
  const file = sideOf(info) === 'jx' ? '../target/jx-server/server.cjs' : '../target/reference-site/server.cjs';
  const module = await import(file);
  return (module.default || module).server;
}

/** A reference-only case that runs React's server APIs in Node, without a page. */
export function serverOnly(title, body) {
  base(title, async ({}, info) => {
    if (sideOf(info) === 'jx') {
      info.annotations.push({type: 'unsupported'});
      return;
    }
    const module = await import('../target/reference-site/server.cjs');
    await body((module.default || module).server);
  });
}
