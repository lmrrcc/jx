import {test, expect, interaction} from './bench.js';

test('combined-optimistic-store: optimistic state rebases on remote snapshots, rolls back rejection and commits one item on retry', async ({page, open}) => {
  await open('combined-optimistic-store');
  const click = name => page.getByRole('button', {name, exact: true}).click();
  const visible = page.getByRole('list', {name: 'Visible items', exact: true});
  const committed = page.getByRole('list', {name: 'Committed items', exact: true});
  const pending = page.getByTestId('pending');
  const add = page.getByRole('button', {name: 'Add optimistic item', exact: true});
  const inspect = async (subscribers, requests, closed) => {
    await click('Inspect ownership');
    await expect(page.getByTestId('ownership')).toHaveText(`subscribers=${subscribers};requests=${requests};closed=${closed}`);
  };
  await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved']);
  await expect(committed.getByRole('listitem')).toHaveText(['Seed']);
  await inspect(1, 0, false);

  await interaction(['optimistic-store', 'optimistic-transition', 'optimistic-async'], async () => {
    await add.click();
    await expect(pending).toHaveText('pending');
    await expect(add).toBeDisabled();
    await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved', 'Local: sending']);
    await expect(visible.locator('[data-id="local"]')).toHaveAttribute('data-state', 'optimistic');
    await expect(committed.getByRole('listitem')).toHaveText(['Seed']);
    await inspect(1, 1, false);

    // An independent synchronous store publication rebases the pending optimistic reducer.
    await click('Publish remote item');
    await expect(committed.getByRole('listitem')).toHaveText(['Seed', 'Remote']);
    await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved', 'Remote: saved', 'Local: sending']);
    await expect(pending).toHaveText('pending');

    // The native async action catches rejection. Only its optimistic addition rolls back.
    await click('Reject request');
    await expect(pending).toHaveText('idle');
    await expect(page.getByRole('status')).toHaveText('failed rejected');
    await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved', 'Remote: saved']);
    await expect(committed.getByRole('listitem')).toHaveText(['Seed', 'Remote']);
    await expect(visible.locator('[data-id="local"]')).toHaveCount(0);
    await inspect(1, 0, false);

    await add.click();
    await expect(pending).toHaveText('pending');
    await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved', 'Remote: saved', 'Local: sending']);
    await inspect(1, 1, false);
    await click('Accept request');
    await expect(committed.getByRole('listitem')).toHaveText(['Seed', 'Remote', 'Local']);
    // A second real await keeps the action pending after commit, exposing any duplicate overlay.
    await expect(pending).toHaveText('pending');
    await expect(add).toBeDisabled();
    await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved', 'Remote: saved', 'Local: saved']);
    await expect(visible.locator('[data-id="local"]')).toHaveCount(1);
    await expect(visible.locator('[data-id="local"]')).toHaveAttribute('data-state', 'confirmed');
    await expect(visible.locator('[data-state="optimistic"]')).toHaveCount(0);
    await inspect(1, 1, false);

    await click('Finish action');
    await expect(pending).toHaveText('idle');
    await expect(page.getByRole('status')).toHaveText('saved');
    await expect(add).toBeEnabled();
    await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved', 'Remote: saved', 'Local: saved']);
    await expect(committed.locator('[data-id="local"]')).toHaveCount(1);
    await inspect(1, 0, false);

    await click('Close editor');
    await expect(page.getByRole('region', {name: 'Optimistic editor', exact: true})).toHaveCount(0);
    await inspect(0, 0, true);
    // A fresh page has its own immutable snapshot and independent request owner.
    await open('combined-optimistic-store');
    await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved']);
    await expect(committed.getByRole('listitem')).toHaveText(['Seed']);
    await inspect(1, 0, false);

    // Close while waiting for the response, then while waiting after a successful
    // commit. close() settles owned gates; this does not claim automatic framework
    // cancellation on unmount. The finally path must release the actual attempt.
    for (const accepted of [false, true]) {
      await add.click();
      await expect(pending).toHaveText('pending');
      await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved', 'Local: sending']);
      await inspect(1, 1, false);
      if (accepted) {
        await click('Accept request');
        await expect(committed.getByRole('listitem')).toHaveText(['Seed', 'Local']);
        await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved', 'Local: saved']);
        await expect(pending).toHaveText('pending');
        await inspect(1, 1, false);
      }
      await click('Close editor');
      await expect(page.getByRole('region', {name: 'Optimistic editor', exact: true})).toHaveCount(0);
      await expect.poll(async () => {
        await click('Inspect ownership');
        return page.getByTestId('ownership').textContent();
      }).toBe('subscribers=0;requests=0;closed=true');
      await expect(page.getByRole('button', {name: 'Publish remote item', exact: true})).toBeDisabled();
      await expect(page.getByRole('button', {name: 'Close editor', exact: true})).toBeDisabled();
      if (!accepted) {
        await open('combined-optimistic-store');
        await expect(visible.getByRole('listitem')).toHaveText(['Seed: saved']);
        await expect(committed.getByRole('listitem')).toHaveText(['Seed']);
        await inspect(1, 0, false);
      }
    }
  });
});
