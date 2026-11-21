import { Suspense, lazy } from 'react';

let load = () => {};
const Late = lazy(() => new Promise(resolve => {
  load = () => resolve({ default: function Late() { return <p role="status">late content</p>; } });
}));

export function LazyComponent() {
  return (
    <main>
      <button onClick={() => load()}>Load</button>
      <Suspense fallback={<p role="status">loading</p>}>
        <Late />
      </Suspense>
    </main>
  );
}
