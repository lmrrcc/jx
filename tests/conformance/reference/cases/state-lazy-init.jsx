import { useState } from 'react';
import { log } from '../bench.js';

export function StateLazyInit() {
  const [value, setValue] = useState(() => {
    log('init');
    return 7;
  });
  return (
    <main>
      <p role="status">{value}</p>
      <button onClick={() => setValue(previous => previous + 1)}>Increment</button>
    </main>
  );
}
