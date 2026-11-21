import {test, expect} from '@playwright/test';
import {installedExports, recordedExports} from '../inventory.mjs';

test('react-exports: the recorded inventory equals the installed React packages', async ({}, info) => {
  test.skip(info.project.name !== 'reference', 'package inventory is independent of the implementation');
  expect(await recordedExports()).toEqual(await installedExports());
});
