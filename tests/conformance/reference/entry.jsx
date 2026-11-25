// Framework-owned reference fixture: the same cases in plain React.
import { StrictMode } from 'react';
import { createRoot, hydrateRoot } from 'react-dom/client';
import { renderToString } from 'react-dom/server';
import { caseId, mode, unknown } from './bench.js';
import { cases } from './cases.jsx';
import { rootOptions } from './cases/root-options.jsx';
import { startActUtilities } from './act-utilities.jsx';


const id = caseId();
const Case = Object.hasOwn(cases, id) ? cases[id] : null;
if (id === 'act-utilities') {
  startActUtilities();
} else if (id === 'hydrate-root') {
  // Server markup first, then hydration over the same nodes.
  const container = document.getElementById('app');
  container.innerHTML = renderToString(<StrictMode><Case /></StrictMode>);
  container.querySelector('button').benchServerNode = true;
  hydrateRoot(container, <StrictMode><Case /></StrictMode>);
} else if (id === 'combined-resource-hydration') {
  // The test server delivered this document; no client-side reseeding.
  hydrateRoot(document.getElementById('app'), <StrictMode><Case /></StrictMode>);
} else if (id === 'hydrate-server') {
  // The markup was rendered in Node; the test hands it to the page before the script runs.
  const container = document.getElementById('app');
  container.innerHTML = globalThis.__benchServerHtml;
  container.querySelector('button').benchServerNode = true;
  hydrateRoot(container, <StrictMode><Case /></StrictMode>);
} else if (id === 'root-options') {
  // The only case with root options: an identifier prefix and the three error callbacks.
  createRoot(document.getElementById('app'), rootOptions).render(<StrictMode><Case /></StrictMode>);
} else if (id === 'strict-nested') {
  // The only case without a root StrictMode: it places StrictMode itself.
  createRoot(document.getElementById('app')).render(<Case />);
} else if (Case && mode() === 'hydrate') {
  // Hydrate mode: any case, over the markup the test rendered in Node for it.
  const container = document.getElementById('app');
  container.innerHTML = globalThis.__benchServerHtml;
  hydrateRoot(container, <StrictMode><Case /></StrictMode>);
} else if (Case) {
  createRoot(document.getElementById('app')).render(<StrictMode><Case /></StrictMode>);
} else {
  unknown(id);
}
