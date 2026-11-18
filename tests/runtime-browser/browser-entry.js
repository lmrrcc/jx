import * as React from 'react';
import { createRoot } from 'react-dom/client';
import { main } from './target/browser/application.js';
globalThis.ForeignBadge = function ForeignBadge({ label }) {
  const [count, setCount] = React.useState(0);
  return React.createElement('button', {id: 'foreign', onClick: () => setCount(n => n + 1)}, `${label}: ${count}`);
};
export { main };
if (!globalThis.__JX_HOT__?.deferMain) {
  main([]);
  createRoot(document.getElementById('export')).render(React.createElement(globalThis.ExportedCounter, {initial: 20}));
}
