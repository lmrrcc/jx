import {test, expect, log} from './bench.js';

test('class-next-context: native three-argument policy observes old current values and pending props state context without eager mutation', async ({page, open}) => {
  await open('class-next-context');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const output = page.getByTestId('policy-provided');
  const authored = page.getByTestId('policy-authored');
  const value = async text => {
    await expect(output).toHaveText(text);
    await expect(authored).toHaveText(text);
  };
  const mark = async () => (await log(page)).length;
  const since = async offset => (await log(page)).slice(offset);
  const saw = async (offset, entry) => {
    const mirror = entry.includes(':provided:') ? entry.replace(':provided:', ':authored:')
      : entry.replace('policy:', 'authored-policy:');
    await expect.poll(() => since(offset)).toEqual(expect.arrayContaining([entry, mirror]));
  };
  await value('0/0/light:born=light');
  await expect(page.getByTestId('policy-default')).toHaveText('0/0/default:born=default');
  await expect(page.getByTestId('policy-null')).toHaveText('0/0/<null>:born=<null>');
  expect((await log(page)).filter(value => /^policy:(provided|authored):old=/.test(value))).toEqual([]);
  const original = await output.elementHandle();
  const originalAuthored = await authored.elementHandle();
  try {
    let offset = await mark();
    await click('Increment context-policy state');
    await saw(offset, 'policy:callback:0/1/light');
    await saw(offset, 'policy:provided:old=0/0/light:next=0/1/light:identity=true');
    await saw(offset, 'policy:handler:0/0/light');
    await value('0/0/light:born=light');
    expect((await since(offset)).filter(value => /^policy:commit:(provided|authored):/.test(value))).toEqual([]);
    await click('Inspect context-policy');
    await expect(page.getByTestId('policy-inspection')).toHaveText('0/1/light');
    await expect(page.getByTestId('authored-policy-inspection')).toHaveText('0/1/light');

    offset = await mark();
    await click('Change context-policy props');
    await expect(page.getByTestId('policy-revision')).toHaveText('1');
    await saw(offset, 'policy:provided:old=0/1/light:next=1/1/light:identity=true');
    await value('0/0/light:born=light');
    await click('Inspect context-policy');
    await expect(page.getByTestId('policy-inspection')).toHaveText('1/1/light');
    await expect(page.getByTestId('authored-policy-inspection')).toHaveText('1/1/light');

    offset = await mark();
    await click('Force context-policy');
    await saw(offset, 'policy:forced:1/1/light');
    await value('1/1/light:born=light');
    expect((await since(offset)).filter(value => /^policy:(provided|authored):old=/.test(value))).toEqual([]);

    offset = await mark();
    await click('Dark context-policy');
    await saw(offset, 'policy:provided:old=1/1/light:next=1/1/dark:identity=true');
    await value('1/1/dark:born=light');
    await expect(page.getByTestId('policy-allow')).toHaveText('no');
    await saw(offset, 'policy:commit:provided:previous=1/1:current=1/1/dark');
    offset = await mark();
    await click('Null context-policy');
    await saw(offset, 'policy:provided:old=1/1/dark:next=1/1/<null>:identity=true');
    await value('1/1/<null>:born=light');

    await click('Allow context-policy');
    await expect(page.getByTestId('policy-allow')).toHaveText('yes');
    offset = await mark();
    await click('Combined context-policy update');
    await saw(offset, 'policy:provided:old=1/1/<null>:next=2/2/dark:identity=true');
    await saw(offset, 'policy:callback:2/2/dark');
    await saw(offset, 'policy:handler:1/1/<null>');
    await value('2/2/dark:born=light');
    await saw(offset, 'policy:commit:provided:previous=1/1:current=2/2/dark');
    await click('Increment context-policy state');
    await value('2/3/dark:born=light');
    expect((await log(page)).filter(value => value.startsWith('legacy-policy:'))).toEqual([]);
    expect(await output.evaluate((node, before) => node === before, original)).toBe(true);
    expect(await authored.evaluate((node, before) => node === before, originalAuthored)).toBe(true);
  } finally {
    await original.dispose();
    await originalAuthored.dispose();
  }
});
