import {test, expect, log, interaction} from './bench.js';

test('combined-action-queue: submitted actions serialize captured payloads, report pending status and recover after rejection', async ({page, open}) => {
  // Only the intentional rejected action may produce a boundary console report.
  // Every report is checked below; page errors and unrelated console failures fail the case.
  const reports = await open('combined-action-queue', {expectErrors: true});
  const field = page.getByRole('textbox', {name: 'Step'});
  const submit = page.getByRole('button', {name: 'Queue step', exact: true});
  const release = page.getByRole('button', {name: 'Release action', exact: true});
  const pending = page.getByTestId('queue-pending');
  const formStatus = page.getByTestId('queue-form-status');
  const total = page.getByTestId('queue-total');
  // The browser form's method is the hook's contract. Client-created and
  // server-rendered function-action forms need not expose the same DOM default.
  const statusFor = async step => 'pending '
    + await page.locator('form').evaluate(form => form.method) + ' ' + step;
  const trace = async () => (await log(page)).filter(entry => entry.startsWith('queue '));
  const firstStart = 'queue start 0:1 previous=0 step=2';
  const firstFinish = 'queue finish 0:1 total=2';
  const secondStart = 'queue start 0:2 previous=2 step=5';
  const secondFinish = 'queue finish 0:2 total=7';

  await interaction(['action-queue-form', 'action-queue-status', 'action-queue-async'], async () => {
    await expect(pending).toHaveText('idle');
    await expect(formStatus).toHaveText('idle');
    await expect(total).toHaveText('0');
    await submit.click();
    await expect(pending).toHaveText('pending');
    await expect(formStatus).toHaveText(await statusFor('2'));
    await expect.poll(trace).toEqual([firstStart]);

    await field.fill('5');
    await submit.click();
    await expect(formStatus).toHaveText(await statusFor('5'));
    await field.fill('99');
    await expect(field).toHaveValue('99');
    // Completing DOM edits proves the page remains live while the first action is gated.
    // The second action must not start until that first promise resolves.
    expect(await trace()).toEqual([firstStart]);
    await expect(formStatus).toHaveText(await statusFor('5'));
    await expect(pending).toHaveText('pending');
    await expect(total).toHaveText('0');

    await release.click();
    await expect.poll(trace).toEqual([firstStart, firstFinish, secondStart]);
    await expect(pending).toHaveText('pending');
    await expect(formStatus).toHaveText(await statusFor('5'));
    await release.click();
    await expect.poll(trace).toEqual([firstStart, firstFinish, secondStart, secondFinish]);
    await expect(total).toHaveText('7');
    await expect(pending).toHaveText('idle');
    await expect(formStatus).toHaveText('idle');
    await expect(field).toHaveValue('2');
    expect(reports).toEqual([]);
  });

  await interaction('action-queue-error', async () => {
    await page.getByRole('button', {name: 'Retry form', exact: true}).click();
    await expect(page.getByTestId('queue-generation')).toHaveText('1');
    await expect(total).toHaveText('0');
    const beforeFailure = await trace();
    const failingStart = 'queue start 1:1 previous=0 step=3';
    await field.fill('3');
    await submit.click();
    await expect(formStatus).toHaveText(await statusFor('3'));
    await field.fill('9');
    await submit.click();
    await expect(formStatus).toHaveText(await statusFor('9'));
    await field.fill('111');
    expect(await trace()).toEqual([...beforeFailure, failingStart]);
    expect(reports).toEqual([]);

    await page.getByRole('button', {name: 'Fail action', exact: true}).click();
    await expect(page.getByRole('alert')).toHaveText('failed: queued action failed');
    await expect(submit).toHaveCount(0);
    await expect(page.getByTestId('queue-sibling')).toHaveText('sibling survives');
    expect(await trace()).toEqual([...beforeFailure, failingStart]);
    await expect.poll(() => reports.length).toBeGreaterThan(0);
    expect(reports.every(report => report.startsWith('console: ')
      && report.includes('queued action failed'))).toBe(true);
    const expectedReports = [...reports];

    await page.getByRole('button', {name: 'Retry form', exact: true}).click();
    await expect(page.getByTestId('queue-generation')).toHaveText('2');
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(total).toHaveText('0');
    await expect(formStatus).toHaveText('idle');
    await field.fill('4');
    await submit.click();
    await expect(formStatus).toHaveText(await statusFor('4'));
    await release.click();
    await expect(total).toHaveText('4');
    await expect(pending).toHaveText('idle');
    await expect(formStatus).toHaveText('idle');
    await expect(field).toHaveValue('2');
    // No '1:2' entry: rejecting the first action canceled its queued successor.
    expect(await trace()).toEqual([...beforeFailure, failingStart,
      'queue start 2:1 previous=0 step=4', 'queue finish 2:1 total=4']);
    expect(reports).toEqual(expectedReports);
  });

  // Replacing a pending generation must settle its consumer and drain queued
  // callbacks without running their domain work against the replacement form.
  const expectedReports = [...reports];
  const beforeDisposal = await trace();
  await field.fill('6');
  await submit.click();
  await expect(formStatus).toHaveText(await statusFor('6'));
  await field.fill('7');
  await submit.click();
  await expect(formStatus).toHaveText(await statusFor('7'));
  await page.getByRole('button', {name: 'Retry form', exact: true}).click();
  await expect(page.getByTestId('queue-generation')).toHaveText('3');
  await expect(total).toHaveText('0');
  await expect(pending).toHaveText('idle');
  await expect.poll(() => log(page)).toContain('action cancelled 2:2');
  await expect.poll(() => log(page)).toContain('action ignored 2');
  expect(await trace()).toEqual([...beforeDisposal, 'queue start 2:2 previous=4 step=6']);

  // Cleanup belongs to the mounted form as well: an unmount without Retry must
  // settle the old queue, and a new generation starts from its own initial state.
  await field.fill('8');
  await submit.click();
  await expect(formStatus).toHaveText(await statusFor('8'));
  await field.fill('9');
  await submit.click();
  await expect(formStatus).toHaveText(await statusFor('9'));
  await page.getByRole('button', {name: 'Unmount form', exact: true}).click();
  await expect(page.getByTestId('queue-owner')).toHaveCount(0);
  await expect.poll(() => log(page)).toContain('action cancelled 3:1');
  await expect.poll(() => log(page)).toContain('action ignored 3');
  await expect(page.getByTestId('queue-sibling')).toHaveText('sibling survives');
  await page.getByRole('button', {name: 'Mount form', exact: true}).click();
  await expect(page.getByTestId('queue-generation')).toHaveText('4');
  await expect(total).toHaveText('0');
  await expect(pending).toHaveText('idle');
  await expect(field).toHaveValue('2');
  await submit.click();
  await expect(pending).toHaveText('pending');
  await release.click();
  await expect(total).toHaveText('2');
  await expect(pending).toHaveText('idle');
  expect(await trace()).toEqual([...beforeDisposal,
    'queue start 2:2 previous=4 step=6', 'queue start 3:1 previous=0 step=8',
    'queue start 4:1 previous=0 step=2', 'queue finish 4:1 total=2']);
  expect(reports).toEqual(expectedReports);
});
