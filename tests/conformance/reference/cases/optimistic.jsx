import { startTransition, useOptimistic, useState } from 'react';

let settle = () => {};

export function Optimistic() {
  const [saved, setSaved] = useState('saved');
  const [shown, setShown] = useOptimistic(saved);
  return (
    <main>
      <button onClick={() => startTransition(async () => {
        setShown('sending');
        await new Promise(resolve => { settle = resolve; });
        startTransition(() => setSaved('confirmed'));
      })}>Send</button>
      <button onClick={() => settle()}>Settle</button>
      <p role="status">{shown}</p>
    </main>
  );
}
