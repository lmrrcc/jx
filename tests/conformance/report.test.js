// Framework test fixture: exercise the reporter's public Playwright callbacks against isolated evidence.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {copyFile, mkdir, mkdtemp, readFile, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {pathToFileURL} from 'node:url';

const projects = ['reference', 'jx', 'reference-hydrate', 'jx-hydrate',
  'reference-static', 'jx-static', 'reference-lazy', 'jx-lazy'];
const flags = ['BENCH_REQUIRE_COMPLETE', 'BENCH_UPDATE', 'BENCH_NO_COMPARE'];
const recorded = ['case\t' + projects.join('\t'),
  ...['first', 'second'].map(id => id + '\t' + projects.map(() => 'pass').join('\t'))].join('\n') + '\n';

const scenarios = [
  {name: 'complete matrix is accepted', complete: true},
  {name: 'missing recorded case fails the full gate', complete: true, ids: ['first'], failed: true},
  {name: 'missing project fails the full gate', complete: true, projects: projects.slice(0, -1), failed: true},
  {name: 'equal-size case replacement cannot hide a missing case', complete: true, ids: ['first', 'replacement'], failed: true},
  {name: 'partial update is rejected without rewriting evidence', update: true, ids: ['first'], failed: true},
  {name: 'focused developer run is permitted without rewriting evidence', ids: ['first']}
];

for (const scenario of scenarios) {
  test(scenario.name, async t => {
    const scratch = await mkdtemp(join(tmpdir(), 'jx-conformance-report-'));
    t.after(() => rm(scratch, {recursive: true, force: true}));
    await mkdir(join(scratch, 'tests/conformance'), {recursive: true});
    await mkdir(join(scratch, 'docs/evidence'), {recursive: true});
    const report = join(scratch, 'tests/conformance/report.mjs');
    const evidence = join(scratch, 'docs/evidence/conformance-results.tsv');
    await copyFile(new URL('./report.js', import.meta.url), report);
    await writeFile(evidence, recorded);
    const Reporter = (await import(pathToFileURL(report).href)).default;
    const reporter = new Reporter();
    for (const id of scenario.ids || ['first', 'second']) {
      for (const project of scenario.projects || projects) {
        reporter.onTestEnd({title: id + ': behavior', annotations: [],
          parent: {project: () => ({name: project})}}, {status: 'passed'});
      }
    }
    t.mock.method(console, 'log', () => {});
    const saved = Object.fromEntries(flags.map(key => [key, process.env[key]]));
    let result;
    try {
      for (const key of flags) delete process.env[key];
      if (scenario.complete) process.env.BENCH_REQUIRE_COMPLETE = '1';
      if (scenario.update) process.env.BENCH_UPDATE = '1';
      result = reporter.onEnd();
    } finally {
      for (const key of flags) {
        if (saved[key] === undefined) delete process.env[key];
        else process.env[key] = saved[key];
      }
    }
    assert.equal(result?.status, scenario.failed ? 'failed' : undefined);
    assert.equal(await readFile(evidence, 'utf8'), recorded);
  });
}
