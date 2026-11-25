import {test, expect} from './bench.js';

test('image-preload: responsive candidates, request privacy and resource identity follow React across rendering modes', async ({page, open}) => {
  const requests = [];
  await page.route('**/__bench/images/**', async route => {
    const request = route.request();
    requests.push({path: new URL(request.url()).pathname, headers: await request.allHeaders()});
    await route.fulfill({contentType: 'image/svg+xml', body:
      '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="blue"/></svg>'});
  });
  await open('image-preload');
  const links = page.locator('link[rel="preload"][as="image"]');
  const narrow = links.and(page.locator('[imagesizes="30px"]'));
  const legacy = links.and(page.locator('[href="/__bench/images/legacy.svg"]'));
  const paths = () => requests.map(request => request.path);
  const selected = '/__bench/images/small.svg';
  await expect(page.getByTestId('image-status')).toHaveText('narrow:0');
  await expect(links).toHaveCount(2);
  await expect(narrow).toHaveCount(1);
  await expect(narrow).toHaveAttribute('imagesrcset', '/__bench/images/small.svg 40w, /__bench/images/large.svg 80w');
  // React normalizes anonymous CORS to the empty HTML attribute value.
  await expect(narrow).toHaveAttribute('crossorigin', '');
  await expect(narrow).toHaveAttribute('fetchpriority', 'high');
  await expect(narrow).toHaveAttribute('type', 'image/svg+xml');
  await expect(narrow).toHaveAttribute('referrerpolicy', 'no-referrer');
  expect(await narrow.getAttribute('href')).toBeNull();
  await expect(legacy).toHaveAttribute('fetchpriority', 'low');
  expect(await legacy.getAttribute('imagesrcset')).toBeNull();
  expect(await legacy.getAttribute('imagesizes')).toBeNull();
  expect(await legacy.getAttribute('referrerpolicy')).toBeNull();
  await expect.poll(paths).toContain(selected);
  await expect.poll(paths).toContain('/__bench/images/legacy.svg');
  expect(requests.find(request => request.path === selected).headers.referer).toBeUndefined();
  expect(new URL(requests.find(request => request.path === '/__bench/images/legacy.svg').headers.referer).origin)
    .toBe(new URL(page.url()).origin);

  const original = await narrow.elementHandle();
  expect(original).not.toBeNull();
  try {
    await page.getByRole('button', {name: 'Repeat responsive preload', exact: true}).click();
    await expect(page.getByTestId('image-status')).toHaveText('narrow:1');
    await expect(links).toHaveCount(2);
    expect(await narrow.evaluate((node, previous) => node === previous, original)).toBe(true);

    await page.getByRole('button', {name: 'Preload wide image', exact: true}).click();
    await expect(page.getByTestId('image-status')).toHaveText('wide:1');
    await expect(links).toHaveCount(3);
    const wide = links.and(page.locator('[imagesizes="70px"]'));
    await expect(wide).toHaveCount(1);
    await expect(wide).toHaveAttribute('imagesrcset', '/__bench/images/small.svg 40w, /__bench/images/large.svg 80w');
    await expect(wide).toHaveAttribute('referrerpolicy', 'no-referrer');
    await expect.poll(paths).toContain('/__bench/images/large.svg');
    expect(requests.find(request => request.path === '/__bench/images/large.svg').headers.referer).toBeUndefined();
    expect(await narrow.evaluate((node, previous) => node === previous, original)).toBe(true);
    expect(paths().some(path => path.includes('fallback'))).toBe(false);
  } finally { await original.dispose(); }
});
