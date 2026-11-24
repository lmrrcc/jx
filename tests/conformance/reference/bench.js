// Framework-owned reference fixture. Mirrors jx.conformance.Bench.
export function caseId() {
  return new URLSearchParams(location.search).get('case') || '';
}

/** The rendering mode of the page: 'client' (the default) or 'hydrate'. */
export function mode() {
  return new URLSearchParams(location.search).get('mode') || 'client';
}

export function log(entry) {
  (globalThis.__benchLog = globalThis.__benchLog || []).push(entry);
}

export function unknown(id) {
  const element = document.getElementById('app');
  element.setAttribute('data-bench', 'unknown');
  element.textContent = 'unknown case: ' + id;
}
