import {Activity, memo, useEffect, useState, useSyncExternalStore} from 'react';
import {log} from '../bench.js';

const listeners = new Set();
let value = 0;
const store = {
  snapshot: () => value,
  serverSnapshot: () => 0,
  subscribe(listener) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  increment() {
    value++;
    [...listeners].forEach(listener => listener());
  }
};

const Reader = memo(function Reader() {
  const snapshot = useSyncExternalStore(store.subscribe, store.snapshot, store.serverSnapshot);
  const [local, setLocal] = useState(0);
  useEffect(() => {
    log('activity mounted');
    return () => log('activity cleaned');
  }, []);
  return <section aria-label="Activity reader">
    <p data-testid="store-value">{snapshot}</p>
    <p data-testid="local-value">{local}</p>
    <button onClick={() => setLocal(previous => previous + 1)}>Increment local</button>
  </section>;
});

export function CombinedActivityStore() {
  const [visible, setVisible] = useState(true);
  const [attached, setAttached] = useState(true);
  const [subscriptions, setSubscriptions] = useState('uninspected');
  return <main>
    <button onClick={() => setVisible(previous => !previous)}>Toggle visibility</button>
    <button onClick={() => setAttached(previous => !previous)}>Toggle attachment</button>
    <button onClick={() => store.increment()}>Publish store</button>
    <button onClick={() => setSubscriptions(String(listeners.size))}>Inspect subscriptions</button>
    <output data-testid="subscriptions">{subscriptions}</output>
    {attached ? <Activity mode={visible ? 'visible' : 'hidden'}><Reader /></Activity> : null}
  </main>;
}
