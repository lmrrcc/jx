import { useState } from 'react';

export function KeyEvent() {
  const [draft, setDraft] = useState('');
  const [submitted, setSubmitted] = useState('');
  return (
    <main>
      <input aria-label="Draft" value={draft} onChange={event => setDraft(event.target.value)} onKeyDown={event => {
        if (event.key === 'Enter') {
          setSubmitted(draft);
          setDraft('');
        }
      }} />
      <p role="status">{submitted}</p>
    </main>
  );
}
