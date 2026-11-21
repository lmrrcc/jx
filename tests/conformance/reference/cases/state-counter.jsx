import { useState } from 'react';

export function StateCounter() {
  const [count, setCount] = useState(0);
  return (
    <main>
      <p role="status">{count}</p>
      <button onClick={() => setCount(previous => previous + 1)}>Increment</button>
      <button onClick={() => {
        setCount(previous => previous + 1);
        setCount(previous => previous + 1);
        setCount(previous => previous + 1);
      }}>Add three</button>
      <button onClick={() => {
        setCount(count + 1);
        setCount(count + 1);
      }}>Set from snapshot twice</button>
    </main>
  );
}
