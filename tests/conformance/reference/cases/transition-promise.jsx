import { startTransition, useState, useTransition } from 'react';

let finish = () => {};

export function TransitionPromise() {
  const [isPending, start] = useTransition();
  const [step, setStep] = useState('idle');
  const [result, setResult] = useState('none');
  return (
    <main>
      <button onClick={() => start(async () => {
        // Before the first await the update still belongs to the transition.
        setStep('started');
        await new Promise(resolve => { finish = resolve; });
        startTransition(() => setResult('done'));
      })}>Start</button>
      <button onClick={() => finish()}>Finish</button>
      <p role="status">{isPending ? 'pending' : step + ' ' + result}</p>
    </main>
  );
}
