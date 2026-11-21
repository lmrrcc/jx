import { useState } from 'react';

export function CallableState() {
  const [label, setLabel] = useState(() => () => 'first');
  return (
    <main>
      <p role="status">{label()}</p>
      <button onClick={() => setLabel(() => () => 'second')}>Replace function</button>
    </main>
  );
}
