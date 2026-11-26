import {test, expect} from '@playwright/test';
import {interaction, modeOf, sideOf, serverOf} from './bench.js';

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
      if (sideOf(info) === 'jx') {
        const [expectedFirst, expectedSecond] = await Promise.all([
          reference.resourceHeaders(kind, 'https://first.test', limit, hints, fail),
          reference.resourceHeaders(kind, 'https://other.test', limit, hints, fail)
        ]);
        expect(first).toEqual(expectedFirst);
        expect(second).toEqual(expectedSecond);
      }
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
      expect(second.events).toEqual(first.events);
      expect(second.links).toEqual(first.links.map(link => link?.replaceAll('first.test', 'other.test') ?? null));
      expect(second.html).toBe(first.html.replaceAll('first.test', 'other.test'));
      if (limit === -1 && hints && !fail) {
        await interaction('headers-' + kind + '-preconnect', async () => {
          expect(first.links[0]).toContain('<https://first.test>; rel=preconnect');
          expect(second.links[0]).toContain('<https://other.test>; rel=preconnect');
          expect(first.links[0].match(/rel=preconnect/g)).toHaveLength(1);
          expect(second.links[0].match(/rel=preconnect/g)).toHaveLength(1);
        });
        await interaction('headers-' + kind + '-preload', async () => {
          for (const [result, origin] of [[first, 'first'], [second, 'other']]) {
            expect(result.links[0]).toContain('https://' + origin + '.test/font.woff2');
            expect(result.links[0]).toContain('https://' + origin + '.test/hero.png');
          }
        });
      }
    }
  }
});
