import { useState } from 'react';

export function ErrorReporting() {
  const [broken, setBroken] = useState(false);
  if (broken) throw new Error('render exploded');
  return (
    <main>
      <p role="status">alive</p>
      <button onClick={() => {
        throw new Error('handler exploded');
      }}>Throw in handler</button>
      <button onClick={() => setBroken(true)}>Throw in render</button>
    </main>
  );
}
