import { useRef, useState } from 'react';

export function RefPersist() {
  const clicks = useRef(0);
  const [, setRenders] = useState(0);
  return (
    <main>
      <p role="status">{clicks.current}</p>
      <button onClick={() => { clicks.current += 1; }}>Bump ref</button>
      <button onClick={() => setRenders(previous => previous + 1)}>Render</button>
    </main>
  );
}
