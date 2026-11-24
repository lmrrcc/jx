import {test, expect, serverOf, interaction} from './bench.js';

const STYLE = 'data:text/css,.jx-resource-hydration{}';
const SELECTOR = 'link[rel="preload"][as="style"]';

test('combined-resource-hydration: hydrates server nodes and reuses a deduplicated resource hint', async ({page, open}, info) => {
  const id = 'combined-resource-hydration';
  const html = await (await serverOf(info)).renderCase(id);
  const script = Promise.withResolvers();
  let released = false;
  const release = () => { if (!released) { released = true; script.resolve(); } };

  // Serve the SSR HTML in the actual initial document; pause only the application
  // module so the test can retain handles to nodes that predate React hydration.
  await page.route('**/?case=' + id, async route => {
    const response = await route.fetch();
    const document = await response.text();
    expect(document).toContain('<div id="app"></div>');
    await route.fulfill({response, body: document.replace('<div id="app"></div>', '<div id="app">' + html + '</div>')});
  });
  await page.route('**/application.js', async route => {
    await script.promise;
    await route.continue();
  });

  // `open` owns error collection. It waits for module load while the initial
  // document can already be inspected, without sleeps or hydration timing races.
  const opened = open(id);
  let link;
  let button;
  try {
    await interaction('ssr-preload-dedupe', async () => {
      await expect(page.locator(SELECTOR)).toHaveCount(1);
      await expect(page.locator(SELECTOR)).toHaveAttribute('href', STYLE);
      await expect(page.getByRole('status')).toHaveText('0');
      link = await page.locator(SELECTOR).elementHandle();
      button = await page.getByRole('button', {name: 'Increment'}).elementHandle();
      expect(link).not.toBeNull();
      expect(button).not.toBeNull();
    });

    release();
    await opened;
    await interaction('hydrate-preload-reuse', async () => {
      // A changed counter proves React attached the handler, rather than merely
      // accepting a static page. Each rerender repeats both preload calls.
      await page.getByRole('button', {name: 'Increment'}).click();
      await expect(page.getByRole('status')).toHaveText('1');
      await expect(page.locator(SELECTOR)).toHaveCount(1);
      expect(await page.locator(SELECTOR).evaluate((node, original) => node === original, link)).toBe(true);
      expect(await page.getByRole('button', {name: 'Increment'}).evaluate((node, original) => node === original, button)).toBe(true);
      await page.getByRole('button', {name: 'Increment'}).click();
      await expect(page.getByRole('status')).toHaveText('2');
      await expect(page.locator(SELECTOR)).toHaveCount(1);
      expect(await page.locator(SELECTOR).evaluate((node, original) => node === original, link)).toBe(true);
    });
  } finally {
    release();
    await opened;
    await link?.dispose();
    await button?.dispose();
  }
});
