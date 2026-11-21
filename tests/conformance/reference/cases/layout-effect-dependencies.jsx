import { useLayoutEffect, useState } from 'react';
import { log } from '../bench.js';

export function LayoutEffectDependencies() {
  const [value, setValue] = useState(1);
  const [noise, setNoise] = useState(0);
  useLayoutEffect(() => {
    log('layout ' + value);
    return () => log('cleanup ' + value);
  }, [value]);
  return (
    <main>
      <p role="status">{noise}</p>
      <button onClick={() => setNoise(previous => previous + 1)}>Unrelated render</button>
      <button onClick={() => setValue(previous => previous + 1)}>Change dependency</button>
    </main>
  );
}
