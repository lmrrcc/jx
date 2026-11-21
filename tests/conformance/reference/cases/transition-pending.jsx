import { startTransition, useState, useTransition } from 'react';

let finish = () => {};

export function TransitionPending() {
  const [isPending, start] = useTransition();
  const [result, setResult] = useState('idle');
  return (
    <main>
      <button onClick={() => start(async () => {
        await new Promise(resolve => { finish = resolve; });
        startTransition(() => setResult('done'));
      })}>Start</button>
      <button onClick={() => finish()}>Finish</button>
      <p role="status">{isPending ? 'pending' : result}</p>
    </main>
  );
}
