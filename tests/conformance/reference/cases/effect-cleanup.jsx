import { useEffect, useState } from 'react';
import { log } from '../bench.js';

function EffectProbe({ dependency }) {
  useEffect(() => {
    log('setup ' + dependency);
    return () => log('cleanup ' + dependency);
  }, [dependency]);
  return <p role="status">{dependency}</p>;
}

export function EffectCleanup() {
  const [dependency, setDependency] = useState('a');
  const [shown, setShown] = useState(true);
  const [renders, setRenders] = useState(0);
  return (
    <main>
      <button onClick={() => setDependency('b')}>Change dependency</button>
      <button onClick={() => setRenders(previous => previous + 1)}>Rerender only</button>
      <button onClick={() => setShown(previous => !previous)}>Toggle probe</button>
      <p data-testid="renders">{renders}</p>
      {shown ? <EffectProbe dependency={dependency} /> : null}
    </main>
  );
}
