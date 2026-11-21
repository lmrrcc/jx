import { useLayoutEffect, useRef, useState } from 'react';

export function DomRefFocus() {
  const input = useRef(null);
  const [read, setRead] = useState('');
  useLayoutEffect(() => {
    input.current.focus();
  }, []);
  return (
    <main>
      <input ref={input} aria-label="Field" />
      <button onClick={() => setRead(input.current.value)}>Read field</button>
      <p role="status">{read}</p>
    </main>
  );
}
