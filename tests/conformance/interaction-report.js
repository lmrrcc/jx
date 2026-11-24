// Framework evidence: only successfully executed assertions in a passing case earn pair credit.
import {readFileSync} from 'node:fs';

export const PROJECTS = ['reference', 'jx', 'reference-hydrate', 'jx-hydrate',
  'reference-static', 'jx-static', 'reference-lazy', 'jx-lazy'];
const modeOf = project => project.includes('-') ? project.split('-')[1] : 'client';
const REGISTRY = new URL('./interactions.tsv', import.meta.url);
const FEATURES = new URL('../../docs/evidence/conformance.tsv', import.meta.url);

export class InteractionReport {
  constructor() {
    const lines = readFileSync(REGISTRY, 'utf8').trimEnd().split('\n');
    if (lines.shift() !== 'id\tfeature-a\tfeature-b\tcase\tmodes\tbehavior') {
      throw new Error('interactions.tsv has an invalid header');
    }
    this.witnesses = new Map();
    this.failures = [];
    for (const line of lines.filter(Boolean)) {
      const cells = line.split('\t');
      const [id, first, second, scenario, modes, behavior] = cells;
      if (cells.length !== 6 || cells.some(cell => !cell.trim()) || this.witnesses.has(id)) {
        throw new Error('Invalid or duplicate interaction: ' + line);
      }
      const applies = new Set(modes.split(','));
      if ([...applies].some(mode => !['client', 'hydrate', 'static', 'lazy'].includes(mode))) {
        throw new Error('Unknown interaction mode: ' + id);
      }
      this.witnesses.set(id, {id, first, second, scenario, applies, behavior,
        results: Object.fromEntries(PROJECTS.filter(project => !applies.has(modeOf(project))).map(project => [project, 'n/a']))});
    }
    const active = readFileSync(FEATURES, 'utf8').split('\n').slice(1).filter(Boolean)
      .filter(line => ['supported', 'equivalent'].includes(line.split('\t')[2])).length;
    this.candidatePairs = active * (active - 1) / 2;
  }

  onTestEnd(test, result) {
    const scenario = test.title.split(':')[0];
    const project = test.parent.project().name;
    // Result annotations are scoped to this attempt, unlike a test's accumulated retry metadata.
    const annotations = result.annotations || test.annotations || [];
    const markers = new Set(annotations.filter(item => item.type === 'interaction').map(item => item.description));
    for (const id of markers) {
      const witness = this.witnesses.get(id);
      if (!witness || witness.scenario !== scenario || !PROJECTS.includes(project)
          || !witness.applies.has(modeOf(project))) {
        this.failures.push('Invalid interaction marker ' + id + ' in ' + scenario + ' [' + project + ']');
      }
    }
    const passed = result.status === 'passed' && !annotations.some(item => item.type === 'unsupported');
    for (const witness of this.witnesses.values()) {
      if (witness.scenario !== scenario || !PROJECTS.includes(project) || !witness.applies.has(modeOf(project))) continue;
      witness.results[project] = passed && markers.has(witness.id) ? 'pass' : 'FAIL';
    }
  }

  errors() {
    const errors = [...this.failures];
    for (const witness of this.witnesses.values()) {
      for (const project of PROJECTS) {
        if (witness.results[project] === 'FAIL') {
          errors.push('Interaction ' + witness.id + ' did not pass in ' + witness.scenario + ' [' + project + ']');
        }
      }
    }
    return errors;
  }

  complete() {
    return [...this.witnesses.values()].every(witness => PROJECTS.every(project => witness.results[project] !== undefined));
  }

  table() {
    return ['interaction', ...PROJECTS].join('\t') + '\n'
      + [...this.witnesses.values()].sort((a, b) => a.id.localeCompare(b.id))
        .map(witness => [witness.id, ...PROJECTS.map(project => witness.results[project] || '-')].join('\t')).join('\n')
      + (this.witnesses.size ? '\n' : '');
  }

  summary() {
    const proven = new Set([...this.witnesses.values()]
      .filter(witness => PROJECTS.every(project => witness.results[project] === 'pass' || witness.results[project] === 'n/a'))
      .map(witness => [witness.first, witness.second].sort().join('\t')));
    return 'Interaction evidence: ' + proven.size + '/' + this.candidatePairs
      + ' currently implemented feature pairs have a passing witness in all its declared modes; '
      + (this.candidatePairs - proven.size) + ' remain without complete evidence.';
  }
}
