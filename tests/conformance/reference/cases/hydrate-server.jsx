import { useId, useState, useSyncExternalStore } from 'react';

// A store whose server snapshot differs from the browser one.
const store = { subscribe: () => () => {}, snapshot: () => 'client', serverSnapshot: () => 'server' };

export function HydratedPage() {
  const id = useId();
  const [count, setCount] = useState(0);
  const origin = useSyncExternalStore(store.subscribe, store.snapshot, store.serverSnapshot);
  return (
    <main>
      <label htmlFor={id}>Name</label>
      <input id={id} defaultValue="Ada" />
      <p data-testid="origin">{origin}</p>
      <p role="status">{count}</p>
      <button onClick={() => setCount(previous => previous + 1)}>Increment</button>
    </main>
  );
}
