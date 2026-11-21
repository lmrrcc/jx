import { useState } from 'react';

function LabelledCounter({ label }) {
  const [count, setCount] = useState(0);
  return (
    <section aria-label={label}>
      <h2>{label}</h2>
      <p role="status">{count}</p>
      <button onClick={() => setCount(previous => previous + 1)}>Increment child</button>
    </section>
  );
}

export function PropsUpdate() {
  const [ticks, setTicks] = useState(0);
  const [label, setLabel] = useState('before');
  return (
    <main>
      <p data-testid="ticks">{ticks}</p>
      <button onClick={() => setTicks(previous => previous + 1)}>Rerender parent</button>
      <button onClick={() => setLabel('after')}>Change label</button>
      <LabelledCounter label={label} />
    </main>
  );
}
