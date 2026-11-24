// Framework test fixture: exercise public Playwright callbacks against isolated evidence.
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
const witnesses = [
  {id: 'first-key-state', scenario: 'first', first: 'jsx:key', second: 'react:useState', modes: ['client', 'hydrate', 'lazy']},
  {id: 'second-context-memo', scenario: 'second', first: 'react:memo', second: 'react:useContext', modes: ['client']}
];
const mode = project => project.includes('-') ? project.split('-')[1] : 'client';
const pairRecord = ['interaction\t' + projects.join('\t'), ...witnesses.map(witness =>
  [witness.id, ...projects.map(project => witness.modes.includes(mode(project)) ? 'pass' : 'n/a')].join('\t'))].join('\n') + '\n';

const scenarios = [
  {name: 'complete matrix is accepted and static HTML earns no interaction credit', complete: true},
  {name: 'missing recorded case fails the full gate', complete: true, ids: ['first'], failed: true},
  {name: 'missing project fails the full gate', complete: true, projects: projects.slice(0, -1), failed: true},
  {name: 'equal-size case replacement cannot hide a missing case', complete: true, ids: ['first', 'replacement'], failed: true},
  {name: 'partial update changes neither evidence file', update: true, ids: ['first'], failed: true},
  {name: 'focused developer run is permitted without rewriting evidence', ids: ['first']},
  {name: 'passed case without a required assertion fails even in a focused run', ids: ['first'], missingMarker: true, failed: true},
  {name: 'failed case cannot earn credit from an earlier successful assertion', failedCase: true, failed: true},
  {name: 'unknown assertion marker fails', extraMarker: 'unknown-witness', failed: true},
  {name: 'assertion from the wrong case fails', extraMarker: 'second-context-memo', failed: true},
  {name: 'assertion in an undeclared static mode fails', wrongMode: true, failed: true},
  {name: 'mutation mode still rejects missing assertion evidence', noCompare: true, ids: ['first'], missingMarker: true, failed: true},
  {name: 'valid mutation run may select only the JX side', noCompare: true, projects: ['jx']},
  {name: 'mutation comparison bypass cannot bypass an explicit full-run requirement', noCompare: true, complete: true, projects: ['jx'], failed: true},
  {name: 'complete update records both evidence tables', update: true, newPairRecord: true}
];

for (const scenario of scenarios) {
  test(scenario.name, async t => {
    const scratch = await mkdtemp(join(tmpdir(), 'jx-conformance-report-'));
    t.after(() => rm(scratch, {recursive: true, force: true}));
    await mkdir(join(scratch, 'tests/conformance'), {recursive: true});
    await mkdir(join(scratch, 'docs/evidence'), {recursive: true});
    await writeFile(join(scratch, 'package.json'), '{"type":"module"}\n');
    for (const file of ['report.js', 'interaction-report.js']) {
      await copyFile(new URL('./' + file, import.meta.url), join(scratch, 'tests/conformance', file));
    }
    await writeFile(join(scratch, 'tests/conformance/interactions.tsv'),
      'id\tfeature-a\tfeature-b\tcase\tmodes\tbehavior\n' + witnesses.map(witness =>
        [witness.id, witness.first, witness.second, witness.scenario, witness.modes.join(','), 'Observable interaction'].join('\t')).join('\n') + '\n');
    await writeFile(join(scratch, 'docs/evidence/conformance.tsv'), 'feature\tarea\tjx\n'
      + ['jsx:key', 'react:useState', 'react:memo', 'react:useContext'].map(id => id + '\tapi\tsupported').join('\n') + '\n');
    const evidence = join(scratch, 'docs/evidence/conformance-results.tsv');
    const interactionEvidence = join(scratch, 'docs/evidence/interaction-results.tsv');
    await writeFile(evidence, recorded);
    await writeFile(interactionEvidence, scenario.newPairRecord ? '' : pairRecord);
    const Reporter = (await import(pathToFileURL(join(scratch, 'tests/conformance/report.js')).href)).default;
    const reporter = new Reporter();
    for (const id of scenario.ids || ['first', 'second']) {
      for (const project of scenario.projects || projects) {
        const annotations = witnesses.filter(witness => witness.scenario === id && witness.modes.includes(mode(project)))
          .map(witness => ({type: 'interaction', description: witness.id}));
        if (id === 'first' && project === 'reference') {
          if (scenario.missingMarker) annotations.length = 0;
          if (scenario.extraMarker) annotations.push({type: 'interaction', description: scenario.extraMarker});
        }
        if (scenario.wrongMode && id === 'first' && project === 'reference-static') {
          annotations.push({type: 'interaction', description: 'first-key-state'});
        }
        reporter.onTestEnd({title: id + ': behavior', annotations: [], parent: {project: () => ({name: project})}},
          {status: scenario.failedCase && id === 'first' && project === 'reference' ? 'failed' : 'passed', annotations});
      }
    }
    t.mock.method(console, 'log', () => {});
    const saved = Object.fromEntries(flags.map(key => [key, process.env[key]]));
    let result;
    try {
      for (const key of flags) delete process.env[key];
      if (scenario.complete) process.env.BENCH_REQUIRE_COMPLETE = '1';
      if (scenario.update) process.env.BENCH_UPDATE = '1';
      if (scenario.noCompare) process.env.BENCH_NO_COMPARE = '1';
      result = reporter.onEnd();
    } finally {
      for (const key of flags) {
        if (saved[key] === undefined) delete process.env[key];
        else process.env[key] = saved[key];
      }
    }
    assert.equal(result?.status, scenario.failed ? 'failed' : undefined);
    assert.equal(await readFile(evidence, 'utf8'), recorded);
    assert.equal(await readFile(interactionEvidence, 'utf8'), pairRecord);
    if (scenario.failedCase) assert.match(reporter.interactions.table(), /first-key-state\tFAIL\tpass/);
    if (!scenario.failed && scenario.complete) assert.match(reporter.interactions.summary(), /2\/6 currently implemented feature pairs/);
  });
}
