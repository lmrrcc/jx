import { startTransition, useState } from 'react';

export function StartTransition() {
  const [tab, setTab] = useState('first');
  const [urgent, setUrgent] = useState(0);
  return (
    <main>
      <button onClick={() => {
        setUrgent(previous => previous + 1);
        startTransition(() => setTab('second'));
      }}>Switch tab</button>
      <p role="status">{tab} {urgent}</p>
    </main>
  );
}
