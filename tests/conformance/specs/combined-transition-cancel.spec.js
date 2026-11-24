import {test, expect, interaction} from './bench.js';

test('combined-transition-cancel: urgent input stays responsive while replaced, cancelled and disposed consumers reject late transport results', async ({page, open}) => {
  await open('combined-transition-cancel');
  const audit = async () => (await page.getByTestId('transition-audit').textContent()).split('\n').filter(Boolean);
  const saw = async line => expect.poll(audit).toContain(line);
  const start = async request => page.getByRole('button', {name: 'Start ' + request, exact: true}).click();
  const resolve = async request => page.getByRole('button', {name: 'Resolve ' + request, exact: true}).click();
  const pending = page.getByTestId('transition-pending');
  const result = page.getByTestId('transition-result');
  const input = page.getByLabel('Urgent draft', {exact: true});
  await expect(page.getByTestId('worker-ready')).toHaveText('ready');
  await expect(page.getByTestId('worker-instance')).toHaveText('1');

  await interaction('transition-urgent-input', async () => {
    await start('A');
    await expect(pending).toHaveText('pending');
    await input.fill('urgent A');
    await expect(input).toHaveValue('urgent A');
    // This separate output proves the urgent React state committed, beyond the DOM input value.
    await expect(page.getByTestId('urgent-value')).toHaveText('urgent A');
    await expect(pending).toHaveText('pending');
    await expect(page.getByTestId('transport-A')).toHaveText('pending');
    await expect(result).toHaveText('none');
  });

  await interaction('transition-owner-ref', async () => {
    await start('B');
    await saw('consumer A cancelled');
    await expect(pending).toHaveText('pending');
    await resolve('B');
    await expect(result).toHaveText('B');
    await expect(pending).toHaveText('idle');
    await saw('published B instance 1');
    // A's local consumer settled, so it cannot keep the transition pending with its transport.
    await expect(page.getByTestId('transport-A')).toHaveText('pending');
    await resolve('A');
    await saw('ignored A');
    await expect(page.getByTestId('transport-A')).toHaveText('completed');
    await expect(result).toHaveText('B');

    await start('C');
    await expect(pending).toHaveText('pending');
    await input.fill('urgent C');
    await expect(page.getByTestId('urgent-value')).toHaveText('urgent C');
    await page.getByRole('button', {name: 'Cancel current', exact: true}).click();
    await saw('consumer C cancelled');
    await expect(pending).toHaveText('idle');
    await expect(page.getByTestId('transport-C')).toHaveText('pending');
    await expect(result).toHaveText('B');
    await resolve('C');
    await saw('ignored C');
    await expect(result).toHaveText('B');
    expect((await audit()).filter(line => /^published (A|C) /.test(line))).toEqual([]);

    // The first transport observer settles the old consumer with a real value.
    // A second observer replaces its owner before that consumer's await resumes.
    // Cancellation cannot overwrite a settled outcome, so publication needs the
    // owner token even though this outcome is completed rather than cancelled.
    await page.getByRole('button', {name: 'Start completion race', exact: true}).click();
    await expect(pending).toHaveText('pending');
    await expect(page.getByTestId('transport-RACE_OLD')).toHaveText('pending');
    await resolve('RACE_OLD');
    await saw('consumer RACE_OLD completed');
    await saw('started RACE_NEW');
    const raceTrace = await audit();
    expect(raceTrace.indexOf('started RACE_NEW')).toBeLessThan(raceTrace.indexOf('consumer RACE_OLD completed'));
    await expect(page.getByTestId('transport-RACE_NEW')).toHaveText('pending');
    // Do not yet attach the new consumer to a transition: React can entangle
    // concurrent async actions and hide an incorrectly published intermediate UI.
    // Wait for the old transition to finish before checking the visible result.
    await expect(pending).toHaveText('idle');
    await expect(result).toHaveText('B');
    expect((await audit()).filter(line => line.startsWith('published RACE_OLD '))).toEqual([]);
    await page.getByRole('button', {name: 'Await replacement', exact: true}).click();
    await expect(pending).toHaveText('pending');
    await resolve('RACE_NEW');
    await expect(result).toHaveText('RACE_NEW');
    await expect(pending).toHaveText('idle');
    await saw('published RACE_NEW instance 1');
  });

  await interaction('async-effect-disposal', async () => {
    await start('OLD');
    await expect(pending).toHaveText('pending');
    await page.getByRole('button', {name: 'Unmount worker', exact: true}).click();
    await expect(page.getByTestId('transition-worker')).toHaveCount(0);
    await saw('consumer OLD cancelled');
    await expect(page.getByTestId('transport-OLD')).toHaveText('pending');

    await page.getByRole('button', {name: 'Mount worker', exact: true}).click();
    await expect(page.getByTestId('worker-ready')).toHaveText('ready');
    await expect(page.getByTestId('worker-instance')).toHaveText('2');
    await expect(input).toHaveValue('');
    await expect(result).toHaveText('none');
    await expect(pending).toHaveText('idle');
    await start('FRESH');
    await expect(pending).toHaveText('pending');
    await resolve('FRESH');
    await expect(result).toHaveText('FRESH');
    await expect(pending).toHaveText('idle');
    await saw('published FRESH instance 2');
    await resolve('OLD');
    await saw('ignored OLD');
    await expect(page.getByTestId('transport-OLD')).toHaveText('completed');
    await expect(result).toHaveText('FRESH');
    await expect(input).toHaveValue('');
    expect((await audit()).filter(line => line.startsWith('published OLD '))).toEqual([]);
  });
});
