import { useState } from 'react';

export function HydrateRoot() {
  const [count, setCount] = useState(0);
  return (
    <main>
      <p role="status">{count}</p>
      <button onClick={() => setCount(previous => previous + 1)}>Increment</button>
    </main>
  );
}
