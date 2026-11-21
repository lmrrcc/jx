// Framework-owned reference fixture. Mirrors jx.conformance.Bench.
export function caseId() {
  return new URLSearchParams(location.search).get('case') || '';
}

export function log(entry) {
  (globalThis.__benchLog = globalThis.__benchLog || []).push(entry);
}

export function unknown(id) {
  const element = document.getElementById('app');
  element.setAttribute('data-bench', 'unknown');
  element.textContent = 'unknown case: ' + id;
}
