import { useRef, useState } from 'react';
import { flushSync } from 'react-dom';

export function FlushSync() {
  const [count, setCount] = useState(0);
  const [seen, setSeen] = useState('');
  const status = useRef(null);
  return (
    <main>
      <p role="status" ref={status}>{count}</p>
      <p data-testid="seen">{seen}</p>
      <button onClick={() => {
        flushSync(() => setCount(previous => previous + 1));
        setSeen('dom showed ' + status.current.textContent);
      }}>Flush</button>
    </main>
  );
}
