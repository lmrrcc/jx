import { useState } from 'react';

export function Conditional() {
  const [mode, setMode] = useState(0);
  return (
    <main>
      <button onClick={() => setMode(previous => (previous + 1) % 3)}>Next</button>
      {mode === 0 ? <p data-testid="branch">first</p> : mode === 1 ? <p data-testid="branch">second</p> : null}
    </main>
  );
}
