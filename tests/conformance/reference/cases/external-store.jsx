import { useState, useSyncExternalStore } from 'react';
import { log } from '../bench.js';

const listeners = new Set();
let value = 0;
const store = {
  snapshot: () => value,
  subscribe(listener) {
    listeners.add(listener);
    log('subscribe');
    return () => {
      listeners.delete(listener);
      log('unsubscribe');
    };
  },
  increment() {
    value += 1;
    [...listeners].forEach(listener => listener());
  }
};

function StoreReader() {
  const current = useSyncExternalStore(store.subscribe, store.snapshot);
  return <p role="status">{current}</p>;
}

export function ExternalStoreCase() {
  const [shown, setShown] = useState(true);
  return (
    <main>
      {shown ? <StoreReader /> : null}
      <button onClick={() => store.increment()}>Change store</button>
      <button onClick={() => setShown(previous => !previous)}>Toggle reader</button>
    </main>
  );
}
