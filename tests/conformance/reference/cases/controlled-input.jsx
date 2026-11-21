import { useState } from 'react';

export function ControlledInput() {
  const [text, setText] = useState('');
  return (
    <main>
      <input aria-label="Name" value={text} onChange={event => setText(event.target.value.toUpperCase())} />
      <p role="status">{text}</p>
      <button onClick={() => setText('')}>Clear</button>
    </main>
  );
}
