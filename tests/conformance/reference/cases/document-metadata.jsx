import { useState } from 'react';

export function DocumentMetadata() {
  const [page, setPage] = useState(1);
  return (
    <main>
      <title>{'Bench page ' + page}</title>
      <meta name="description" content={'Description ' + page} />
      <button onClick={() => setPage(previous => previous + 1)}>Next page</button>
      <p role="status">{page}</p>
    </main>
  );
}
