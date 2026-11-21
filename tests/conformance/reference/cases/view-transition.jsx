import { ViewTransition, addTransitionType, startTransition, useState } from 'react';

export function ViewTransitionCase() {
  const [page, setPage] = useState('first');
  return (
    <main>
      <button onClick={() => startTransition(() => {
        addTransitionType('forward');
        setPage('second');
      })}>Go forward</button>
      <ViewTransition><p role="status">{page}</p></ViewTransition>
    </main>
  );
}
