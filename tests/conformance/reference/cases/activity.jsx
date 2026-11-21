import { Activity, useEffect, useState } from 'react';
import { log } from '../bench.js';

function Counter() {
  const [count, setCount] = useState(0);
  useEffect(() => {
    log('effect on');
    return () => log('effect off');
  }, []);
  return (
    <section aria-label="Kept">
      <p data-testid="count">{count}</p>
      <button onClick={() => setCount(previous => previous + 1)}>Increment</button>
    </section>
  );
}

export function ActivityCase() {
  const [visible, setVisible] = useState(true);
  return (
    <main>
      <button onClick={() => setVisible(previous => !previous)}>Toggle activity</button>
      <Activity mode={visible ? 'visible' : 'hidden'}><Counter /></Activity>
    </main>
  );
}
