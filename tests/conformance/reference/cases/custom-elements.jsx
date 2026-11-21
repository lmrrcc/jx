import { useState } from 'react';

export function CustomElements() {
  const [label, setLabel] = useState('first');
  return (
    <main>
      <bench-badge label={label} data-kind="tag" class="badge">content</bench-badge>
      <button onClick={() => setLabel('second')}>Change label</button>
    </main>
  );
}
