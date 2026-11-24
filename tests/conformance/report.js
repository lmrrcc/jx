// Framework test fixture: prints one row per case with the reference and JX result,
// and compares the table with the recorded docs/evidence/conformance-results.tsv.
// Set BENCH_UPDATE=1 to record a deliberate change. The mutation runner sets
// BENCH_NO_COMPARE=1 because it runs the JX side only.
// The full npm command sets BENCH_REQUIRE_COMPLETE=1; focused Playwright runs do not.
import {readFileSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {InteractionReport, PROJECTS} from './interaction-report.js';

const RECORD = fileURLToPath(new URL('../../docs/evidence/conformance-results.tsv', import.meta.url));
const INTERACTIONS = fileURLToPath(new URL('../../docs/evidence/interaction-results.tsv', import.meta.url));

export default class ConformanceReport {
  constructor() { this.rows = new Map(); this.interactions = new InteractionReport(); }

  onTestEnd(test, result) {
    const id = test.title.split(':')[0];
    const row = this.rows.get(id) || {title: test.title.slice(id.length + 2)};
    const unsupported = test.annotations.some(annotation => annotation.type === 'unsupported');
    row[test.parent.project().name] = result.status === 'skipped' ? 'n/a'
      : result.status !== 'passed' ? 'FAIL' : unsupported ? 'unsupported' : 'pass';
    this.rows.set(id, row);
    this.interactions.onTestEnd(test, result);
  }

  onEnd() {
    const rows = [...this.rows.entries()].sort((a, b) => a[0].localeCompare(b[0]));
    const width = Math.max(...rows.map(([id]) => id.length), 4);
    const count = name => rows.filter(([, row]) => row[name] === 'pass').length
      + '/' + rows.filter(([, row]) => row[name] !== undefined && row[name] !== 'n/a').length;
    const line = (id, row) => [id, ...PROJECTS.map(name => row[name] || '-')].join('\t');
    console.log('\nConformance bench: ' + rows.length + ' cases');
    console.log('case'.padEnd(width) + '  reference  jx           behavior');
    for (const [id, row] of rows) {
      console.log(id.padEnd(width) + '  ' + (row.reference || '-').padEnd(9) + '  ' + (row.jx || '-').padEnd(11) + '  ' + row.title);
    }
    console.log('reference: ' + count('reference') + ' pass; jx: ' + count('jx') + ' pass, '
      + rows.filter(([, row]) => row.jx === 'unsupported').length + ' unsupported');
    console.log('hydrate mode: reference ' + count('reference-hydrate') + ' pass; jx ' + count('jx-hydrate') + ' pass');
    console.log('static mode: reference ' + count('reference-static') + ' pass; jx ' + count('jx-static') + ' pass');
    console.log('lazy mode: reference ' + count('reference-lazy') + ' pass; jx ' + count('jx-lazy') + ' pass');
    console.log(this.interactions.summary());

    const table = ['case', ...PROJECTS].join('\t') + '\n' + rows.map(([id, row]) => line(id, row)).join('\n') + '\n';
    const interactionErrors = this.interactions.errors();
    if (interactionErrors.length) {
      interactionErrors.forEach(error => console.log(error));
      return {status: 'failed'};
    }
    // Mutation runs skip golden-file comparison, but never selected assertion checks
    // or an explicitly requested complete run.
    const skipComparison = process.env.BENCH_NO_COMPARE === '1';
    const requireComplete = process.env.BENCH_UPDATE === '1' || process.env.BENCH_REQUIRE_COMPLETE === '1';
    if (skipComparison && !requireComplete) return;
    let recorded = '';
    try { recorded = readFileSync(RECORD, 'utf8'); } catch { /* reported below */ }
    const before = new Map(recorded.split('\n').slice(1).filter(Boolean).map(line => [line.split('\t')[0], line]));
    const missingCases = [...before.keys()].filter(id => !this.rows.has(id));
    if (!rows.length || rows.length < before.size || missingCases.length || !this.interactions.complete()
        || rows.some(([, row]) => PROJECTS.some(name => row[name] === undefined))) {
      if (requireComplete) {
        console.log('Incomplete run: every recorded case must run in every project. Recorded results were not changed.');
        if (missingCases.length) console.log('Missing recorded cases: ' + missingCases.join(', '));
        return {status: 'failed'};
      }
      console.log('Partial run: recorded results were not compared.');
      return;
    }
    if (skipComparison) return;
    if (process.env.BENCH_UPDATE === '1') {
      writeFileSync(RECORD, table);
      writeFileSync(INTERACTIONS, this.interactions.table());
      console.log('Recorded case and interaction results in docs/evidence/.');
      return;
    }
    const changes = [];
    for (const [id, row] of rows) {
      const now = line(id, row);
      if (before.get(id) !== now) changes.push((before.get(id) || id + '\t(new)').replaceAll('\t', ' ') + '  ->  ' + now.replaceAll('\t', ' '));
    }
    let recordedInteractions = '';
    try { recordedInteractions = readFileSync(INTERACTIONS, 'utf8'); } catch { /* comparison fails below */ }
    if (recordedInteractions !== this.interactions.table()) {
      changes.push('docs/evidence/interaction-results.tsv differs from the executed interaction assertions');
    }
    if (changes.length) {
      console.log('\nResults differ from docs/evidence/conformance-results.tsv:');
      changes.forEach(change => console.log('  ' + change));
      console.log('If the change is intended, rerun with BENCH_UPDATE=1 and review the file.');
      return {status: 'failed'};
    }
    console.log('Results match docs/evidence/conformance-results.tsv and interaction-results.tsv.');
  }
}
