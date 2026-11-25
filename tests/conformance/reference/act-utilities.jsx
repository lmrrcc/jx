import * as React from 'react';
import {createRoot} from 'react-dom/client';
import {log} from './bench.js';

function ActConsumer({store}) {
  const value = React.useSyncExternalStore(store.subscribe, store.snapshot);
  React.useEffect(() => {
    log('effect:' + value);
    if (value === 1) store.publish(2);
    return () => log('cleanup:' + value);
  }, [value, store]);
  return <p id="act-count">{value}</p>;
}

export function startActUtilities() {
  const app = document.getElementById('app');
  app.innerHTML = '<div id="act-root"></div>';
  app.dataset.bench = 'act-utilities';
  let value = 0;
  const listeners = new Set();
  const store = {
    snapshot: () => value,
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
    publish(next) { value = next; for (const listener of [...listeners]) listener(); }
  };
  const gate = Promise.withResolvers();
  let root;
  let calls = 0;
  const receipt = {label: 'mounted'};
  const act = async scope => {
    if (typeof React.act !== 'function') throw new Error('React act requires a development build');
    return await React.act(scope);
  };
  const report = async completion => {
    const value = await completion;
    return {value, count: document.getElementById('act-count')?.textContent ?? null, log: [...(globalThis.__benchLog || [])]};
  };
  globalThis.__benchUtilities = {
    mount: () => report(act(async () => {
      calls++;
      root = createRoot(document.getElementById('act-root'));
      root.render(<ActConsumer store={store} />);
      return receipt;
    }).then(returned => {
      if (returned !== receipt) throw new Error('Act changed result identity');
      return returned.label;
    })),
    start: () => report(act(async () => { calls++; await gate.promise; store.publish(1); return 'arrived'; })),
    release: () => gate.resolve(),
    nested: () => report(act(async () => await act(async () => { store.publish(3); return 'nested'; }))),
    fail: () => report(act(async () => { throw new Error('act rejected'); })),
    throwing: () => report(act(() => { throw new Error('act threw'); })),
    sync() {
      const completed = act(() => { calls++; store.publish(4); });
      const immediate = {value: 'immediate', count: document.getElementById('act-count')?.textContent ?? null,
        log: [...(globalThis.__benchLog || [])]};
      return {immediate, completion: report(completed.then(() => 'sync'))};
    },
    syncThrowing() {
      const failure = new Error('sync act threw');
      return report(act(() => { throw failure; }).catch(error => {
        if (error !== failure) throw new Error('Synchronous act lost failure identity');
        throw failure;
      }));
    },
    unmount: () => report(act(async () => { root.unmount(); return 'unmounted'; })),
    calls: () => calls,
    outside: typeof React.captureOwnerStack === 'function' ? React.captureOwnerStack() : null
  };
}
