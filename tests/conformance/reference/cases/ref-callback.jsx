import { useCallback, useState } from 'react';
import { log } from '../bench.js';

export function RefCallback() {
  const [shown, setShown] = useState(true);
  const [ticks, setTicks] = useState(0);
  const attach = useCallback(node => {
    log('attach ' + node.tagName);
    return () => log('detach');
  }, []);
  return (
    <main>
      {shown ? <p ref={attach} role="status">node</p> : null}
      <p data-testid="ticks">{ticks}</p>
      <button onClick={() => setTicks(previous => previous + 1)}>Rerender</button>
      <button onClick={() => setShown(false)}>Remove node</button>
    </main>
  );
}
