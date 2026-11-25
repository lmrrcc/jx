import {test, expect} from '@playwright/test';
import {modeOf, sideOf, serverOf} from './bench.js';

test('server-resource-headers: bounded request-owned hints and callback failures match native React', async ({}, info) => {
  test.skip(modeOf(info) !== 'client', 'server header callbacks have no browser rendering mode');
  const server = await serverOf(info);
  const reference = await serverOf({project: {name: 'reference'}});
  for (const kind of ['readable', 'pipeable', 'prerender', 'prerender-node']) {
    for (const [limit, hints, fail] of [[-1, true, false], [1, true, false], [80, true, false],
      [-1, false, false], [-1, true, true]]) {
      const [first, second] = await Promise.all([
        server.resourceHeaders(kind, 'https://first.test', limit, hints, fail),
        server.resourceHeaders(kind, 'https://other.test', limit, hints, fail)
      ]);
      if (sideOf(info) === 'jx') expect(first).toEqual(
        await reference.resourceHeaders(kind, 'https://first.test', limit, hints, fail));
      expect(first.events).toEqual(fail ? ['headers', 'original-error', 'ready', 'end'] : ['headers', 'ready', 'end']);
      expect(first.links).toHaveLength(1);
      expect(JSON.stringify(first)).not.toContain('other.test');
      expect(JSON.stringify(second)).not.toContain('first.test');
      const link = first.links[0];
      if (!hints || limit === 1) expect(link).toBeNull();
      else {
        expect(link).toContain('<https://first.test>; rel=preconnect');
        expect(link.match(/rel=preconnect/g)).toHaveLength(1);
        if (limit === -1) {
          expect(link).toContain('/font.woff2');
          expect(link).toContain('/hero.png');
        } else expect(link.length).toBeLessThanOrEqual(limit);
      }
      if (hints && limit === 1) {
        expect(first.html).toContain('href="https://first.test/font.woff2"');
        expect(first.html).toContain('href="https://first.test/hero.png"');
      }
      expect(first.html).toContain('<h1>headers</h1>');
    }
  }
});
