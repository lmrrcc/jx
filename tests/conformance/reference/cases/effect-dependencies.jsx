import { useEffect, useState } from 'react';
import { log } from '../bench.js';

export function EffectDependencies() {
  const [renders, setRenders] = useState(0);
  useEffect(() => {
    log('always');
    return () => log('cleanup always');
  });
  useEffect(() => {
    log('once');
    return () => log('cleanup once');
  }, []);
  return (
    <main>
      <p role="status">{renders}</p>
      <button onClick={() => setRenders(previous => previous + 1)}>Rerender</button>
    </main>
  );
}
