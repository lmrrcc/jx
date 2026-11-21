import { useState } from 'react';

export function CheckboxCase() {
  const [accepted, setAccepted] = useState(false);
  return (
    <main>
      <input type="checkbox" aria-label="Accept" checked={accepted} onChange={event => setAccepted(event.target.checked)} />
      <p role="status">{accepted ? 'accepted' : 'pending'}</p>
    </main>
  );
}
