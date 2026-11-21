import { useState } from 'react';

function counter(label) {
  return function Counter() {
    const [count, setCount] = useState(0);
    return (
      <section aria-label={label}>
        <p role="status">{count}</p>
        <button onClick={() => setCount(previous => previous + 1)}>Increment</button>
      </section>
    );
  };
}
const LoudCounter = counter('Loud');
const QuietCounter = counter('Quiet');

export function DynamicComponent() {
  const [loud, setLoud] = useState(false);
  const [renders, setRenders] = useState(0);
  const Chosen = loud ? LoudCounter : QuietCounter;
  return (
    <main>
      <button onClick={() => setLoud(previous => !previous)}>Switch type</button>
      <button onClick={() => setRenders(previous => previous + 1)}>Rerender</button>
      <p data-testid="renders">{renders}</p>
      <Chosen />
    </main>
  );
}
