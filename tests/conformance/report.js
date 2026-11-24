// Framework test fixture: prints one row per case with the reference and JX result,
// and compares the table with the recorded docs/evidence/conformance-results.tsv.
// Set BENCH_UPDATE=1 to record a deliberate change. The mutation runner sets
// BENCH_NO_COMPARE=1 because it runs the JX side only.
import {readFileSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const PROJECTS = ['reference', 'jx', 'reference-hydrate', 'jx-hydrate'];
const RECORD = fileURLToPath(new URL('../../docs/evidence/conformance-results.tsv', import.meta.url));

export default class ConformanceReport {
  constructor() { this.rows = new Map(); }

  onTestEnd(test, result) {
    const id = test.title.split(':')[0];
    const row = this.rows.get(id) || {title: test.title.slice(id.length + 2)};
    const unsupported = test.annotations.some(annotation => annotation.type === 'unsupported');
    row[test.parent.project().name] = result.status === 'skipped' ? 'n/a'
      : result.status !== 'passed' ? 'FAIL' : unsupported ? 'unsupported' : 'pass';
    this.rows.set(id, row);
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

    const table = ['case', ...PROJECTS].join('\t') + '\n' + rows.map(([id, row]) => line(id, row)).join('\n') + '\n';
    if (process.env.BENCH_NO_COMPARE === '1') return;
    if (process.env.BENCH_UPDATE === '1') {
      writeFileSync(RECORD, table);
      console.log('Recorded results in docs/evidence/conformance-results.tsv');
      return;
    }
    let recorded = '';
    try { recorded = readFileSync(RECORD, 'utf8'); } catch { /* reported below */ }
    const before = new Map(recorded.split('\n').slice(1).filter(Boolean).map(line => [line.split('\t')[0], line]));
    if (rows.length < before.size || rows.some(([, row]) => PROJECTS.some(name => row[name] === undefined))) {
      console.log('Partial run: recorded results were not compared.');
      return;
    }
    const changes = [];
    for (const [id, row] of rows) {
      const now = line(id, row);
      if (before.get(id) !== now) changes.push((before.get(id) || id + '\t(new)').replaceAll('\t', ' ') + '  ->  ' + now.replaceAll('\t', ' '));
    }
    if (changes.length) {
      console.log('\nResults differ from docs/evidence/conformance-results.tsv:');
      changes.forEach(change => console.log('  ' + change));
      console.log('If the change is intended, rerun with BENCH_UPDATE=1 and review the file.');
      return {status: 'failed'};
    }
    console.log('Results match docs/evidence/conformance-results.tsv.');
  }
}
