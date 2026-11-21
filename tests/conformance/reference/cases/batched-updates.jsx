import { useEffect, useState } from 'react';
import { log } from '../bench.js';

export function BatchedUpdates() {
  const [left, setLeft] = useState(0);
  const [right, setRight] = useState(0);
  useEffect(() => {
    log(left + ',' + right);
  });
  return (
    <main>
      <p role="status">{left + right}</p>
      <button onClick={() => {
        setLeft(previous => previous + 1);
        setRight(previous => previous + 1);
      }}>Update both</button>
    </main>
  );
}
