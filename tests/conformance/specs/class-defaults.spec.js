import {test, expect} from './bench.js';

test('class-defaults: native defaults resolve missing and undefined inputs without replacing null, zero, false or empty values', async ({page, open}) => {
  await open('class-defaults');
  const fallback = 'required|fallback|7|true|fallback-text|default|null|undefined';
  const values = page.getByTestId('default-values'), derived = page.getByTestId('default-derived');
  await expect(values).toHaveText(fallback);
  await expect(derived).toHaveText(fallback);
  await expect(page.getByTestId('default-constructor')).toHaveText(fallback);
  const old = await page.getByTestId('defaulted-class').elementHandle();
  await page.getByRole('button', {name: 'Remember default peer', exact: true}).click();
  await page.getByRole('button', {name: 'Default clicks: 0', exact: true}).click();
  for (const [action, text, nativeLabel, marker] of [
    ['Use undefined default inputs', fallback, 'fallback', true],
    ['Use explicit empty values', 'required|null|0|false||other|null|undefined', 'null', false],
    ['Use explicit defined values', 'required|explicit|3|true|written|default|value|known', 'explicit', true],
    ['Use missing default inputs', fallback, 'fallback', true],
    ['Clone without overrides', fallback, 'fallback', true],
    ['Clone with undefined label', fallback, 'undefined', true],
    ['Clone undefined again', fallback, 'undefined', true]
  ]) {
    await page.getByRole('button', {name: action, exact: true}).click();
    await expect(values).toHaveText(text);
    await expect(derived).toHaveText(text);
    await expect(page.getByTestId('default-constructor')).toHaveText(fallback);
    await expect(page.getByRole('button', {name: 'Default clicks: 1', exact: true})).toBeVisible();
    expect(await page.getByTestId('defaulted-class').evaluate((node, previous) => node === previous, old)).toBe(true);
    await page.getByRole('button', {name: 'Compare default peer', exact: true}).click();
    await expect(page.getByTestId('default-inspection')).toHaveText('true');
    await page.getByRole('button', {name: 'Inspect native default props', exact: true}).click();
    await expect(page.getByTestId('default-inspection')).toHaveText('true:' + nativeLabel + ':' + marker + ':true');
  }
  await page.getByRole('button', {name: 'Reset default key', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Default clicks: 0', exact: true})).toBeVisible();
  await expect(values).toHaveText(fallback);
  await expect(derived).toHaveText(fallback);
  expect(await page.getByTestId('defaulted-class').evaluate((node, previous) => node === previous, old)).toBe(false);
  await old.dispose();
  await page.getByRole('button', {name: 'Compare default peer', exact: true}).click();
  await expect(page.getByTestId('default-inspection')).toHaveText('false');
});

test('class-defaults-authoring: generated default metadata preserves omission, explicit values, children, state and class refs', async ({page, open}) => {
  await open('class-defaults-authoring');
  const value=page.getByTestId('authored-default').locator('output');
  await expect(value).toHaveText('typed:generated-default:4:4');
  const old=await page.getByTestId('authored-default').elementHandle();
  await page.getByRole('button',{name:'Remember authored peer',exact:true}).click();
  await page.getByRole('button',{name:'Increment authored default',exact:true}).click();
  await expect(value).toHaveText('typed:generated-default:4:5');
  for (const [action,text] of [
    ['Override authored defaults','typed:null:0:5'],
    ['Forward authored props','typed:forwarded:9:5'],
    ['Omit authored defaults','typed:generated-default:4:5']
  ]) {
    await page.getByRole('button',{name:action,exact:true}).click();
    await expect(value).toHaveText(text);
    await expect(page.getByTestId('authored-default').locator('strong')).toHaveCount(action==='Override authored defaults'?1:0);
    expect(await page.getByTestId('authored-default').evaluate((node,previous)=>node===previous,old)).toBe(true);
    await page.getByRole('button',{name:'Compare authored peer',exact:true}).click();
    await expect(page.getByTestId('authored-default-inspection')).toHaveText('true');
  }
  await page.getByRole('button',{name:'Reset authored key',exact:true}).click();
  await expect(value).toHaveText('typed:generated-default:4:4');
  expect(await page.getByTestId('authored-default').evaluate((node,previous)=>node===previous,old)).toBe(false);
  await old.dispose();
  await page.getByRole('button',{name:'Compare authored peer',exact:true}).click();
  await expect(page.getByTestId('authored-default-inspection')).toHaveText('false');
});
