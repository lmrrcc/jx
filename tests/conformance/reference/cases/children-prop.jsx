import { useState } from 'react';

function Panel({ title, children }) {
  return (
    <section aria-label={title}>
      <h2>{title}</h2>
      {children}
    </section>
  );
}

export function ChildrenProp() {
  const [count, setCount] = useState(0);
  return (
    <main>
      <Panel title="Box">
        <p data-testid="first">first</p>
        <p role="status">{count}</p>
      </Panel>
      <button onClick={() => setCount(previous => previous + 1)}>Increment</button>
    </main>
  );
}
