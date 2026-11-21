import { useDeferredValue, useEffect, useState } from 'react';
import { log } from '../bench.js';

export function DeferredValue() {
  const [text, setText] = useState('ready');
  const deferred = useDeferredValue(text, 'initial');
  useEffect(() => { log(deferred); }, [deferred]);
  return (
    <main>
      <p role="status">{deferred}</p>
      <button onClick={() => setText('changed')}>Change</button>
    </main>
  );
}
