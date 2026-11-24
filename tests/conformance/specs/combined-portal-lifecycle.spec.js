import {test, expect, interaction} from './bench.js';

test('combined-portal-lifecycle: context, event phases and committed refs follow the logical tree across a portal', async ({page, open}) => {
  await open('combined-portal-lifecycle');
  const panel = page.getByTestId('portal-panel');
  const input = page.getByLabel('Portal draft', {exact: true});
  const events = async () => (await page.getByTestId('portal-events').textContent()).split('\n').filter(Boolean);
  const line = (phase, current, tag, prevented) =>
    `${phase}|click|portal-action-label|SPAN|${current}|${tag}|${prevented}`;
  const captures = [line('owner-capture', 'portal-owner', 'DIV', false),
    line('panel-capture', 'portal-panel', 'SECTION', false)];
  const target = line('target', 'portal-action', 'A', true);
  const bubbling = [line('panel-bubble', 'portal-panel', 'SECTION', true),
    line('owner-bubble', 'portal-owner', 'DIV', true)];

  await expect(panel).toHaveCount(0);
  await expect(page.getByTestId('portal-layout')).toHaveText('not mounted');
  await page.getByRole('button', {name: 'Open portal', exact: true}).click();
  await expect(panel).toBeVisible();
  const firstInput = await input.elementHandle();
  expect(firstInput).not.toBeNull();

  await interaction('portal-context', async () => {
    expect(await panel.evaluate(node => node.parentElement === document.body)).toBe(true);
    expect(await page.getByTestId('portal-owner').evaluate(node => node.querySelector('#portal-panel'))).toBeNull();
    await expect(page.getByTestId('portal-theme')).toHaveText('Theme: light');
    await input.fill('draft survives context');
    await page.getByRole('button', {name: 'Change portal theme', exact: true}).click();
    await expect(page.getByTestId('portal-theme')).toHaveText('Theme: dark');
    await expect(input).toHaveValue('draft survives context');
    expect(await input.evaluate((node, original) => node === original, firstInput)).toBe(true);
  });

  await interaction(['portal-capture', 'portal-payload'], async () => {
    await page.getByRole('button', {name: 'Clear portal events', exact: true}).click();
    await expect.poll(events).toEqual([]);
    const before = page.url();
    await page.getByTestId('portal-action-label').click();
    await expect.poll(events).toEqual([...captures, target, ...bubbling]);
    // The anchor prevents navigation; bubble handlers see the updated synthetic-event flag.
    expect(page.url()).toBe(before);
  });

  await interaction('portal-propagation', async () => {
    await page.getByRole('button', {name: 'Toggle portal propagation', exact: true}).click();
    await expect(page.getByTestId('portal-propagation-mode')).toHaveText('stopped');
    await page.getByRole('button', {name: 'Clear portal events', exact: true}).click();
    await expect.poll(events).toEqual([]);
    const before = page.url();
    await page.getByTestId('portal-action-label').click();
    await expect.poll(events).toEqual([...captures, target]);
    expect(page.url()).toBe(before);
  });

  await interaction(['portal-dom-ref', 'portal-layout'], async () => {
    await page.getByRole('button', {name: 'Close portal', exact: true}).click();
    await expect(panel).toHaveCount(0);
    expect(await firstInput.evaluate(node => node.isConnected)).toBe(false);
    await expect(page.getByTestId('portal-layout')).toHaveText('cleaned');
    await page.getByRole('button', {name: 'Inspect portal ref', exact: true}).click();
    await expect(page.getByTestId('portal-ref')).toHaveText('cleared');

    await page.getByRole('button', {name: 'Open portal', exact: true}).click();
    await expect(input).toBeFocused();
    await expect(input).toHaveValue('portal draft');
    expect(await input.evaluate((node, original) => node === original, firstInput)).toBe(false);
    const reopenedInput = await input.elementHandle();
    expect(reopenedInput).not.toBeNull();
    await expect(page.getByTestId('portal-layout')).toHaveText('attached INPUT');
    await page.getByRole('button', {name: 'Inspect portal ref', exact: true}).click();
    await expect(page.getByTestId('portal-ref')).toHaveText('attached INPUT');

    await page.getByRole('button', {name: 'Close portal', exact: true}).click();
    await expect(panel).toHaveCount(0);
    expect(await reopenedInput.evaluate(node => node.isConnected)).toBe(false);
    await expect(page.getByTestId('portal-layout')).toHaveText('cleaned');
    await page.getByRole('button', {name: 'Inspect portal ref', exact: true}).click();
    await expect(page.getByTestId('portal-ref')).toHaveText('cleared');
    await reopenedInput.dispose();
  });
  await firstInput.dispose();
});
