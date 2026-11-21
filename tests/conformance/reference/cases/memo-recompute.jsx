import { useMemo, useState } from 'react';
import { log } from '../bench.js';

export function MemoRecompute() {
  const [number, setNumber] = useState(1);
  const [noise, setNoise] = useState(0);
  const doubled = useMemo(() => {
    log('compute ' + number);
    return number * 2;
  }, [number]);
  return (
    <main>
      <p role="status">{doubled}</p>
      <p data-testid="noise">{noise}</p>
      <button onClick={() => setNoise(previous => previous + 1)}>Unrelated render</button>
      <button onClick={() => setNumber(previous => previous + 1)}>Change dependency</button>
    </main>
  );
}
