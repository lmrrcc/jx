import * as React from 'react';
import { createRoot } from 'react-dom/client';
import { main, JxHotRuntime } from './target/browser/application.js';
import {assertHotRuntime, compiledHotRuntimeVersion} from '../../jx-maven-plugin/src/main/resources/jx-hot-protocol.js';
const hotRuntimeVersion = compiledHotRuntimeVersion(JxHotRuntime);
assertHotRuntime(hotRuntimeVersion);
globalThis.ForeignBadge = function ForeignBadge({ label }) {
  const [count, setCount] = React.useState(0);
  return React.createElement('button', {id: 'foreign', onClick: () => setCount(n => n + 1)}, `${label}: ${count}`);
};
export { main, hotRuntimeVersion };
export {resourceLifecycleProbe} from './resource-reference.js';
if (!globalThis.__JX_HOT__?.deferMain) {
  main([]);
  if (globalThis.ExportedCounter) {
    createRoot(document.getElementById('export')).render(React.createElement(globalThis.ExportedCounter, {initial: 20}));
  }
}
