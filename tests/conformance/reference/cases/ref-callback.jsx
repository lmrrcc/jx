import { useCallback, useState } from 'react';
import { log } from '../bench.js';

export function RefCallback() {
  const [shown, setShown] = useState(true);
  const attach = useCallback(node => {
    log('attach ' + node.tagName);
    return () => log('detach');
  }, []);
  return (
    <main>
      {shown ? <p ref={attach} role="status">node</p> : null}
      <button onClick={() => setShown(false)}>Remove node</button>
    </main>
  );
}
