import { useState } from 'react';

export function StopPropagation() {
  const [outer, setOuter] = useState(0);
  const [inner, setInner] = useState(0);
  return (
    <main>
      <div onClick={() => setOuter(previous => previous + 1)}>
        <button onClick={() => setInner(previous => previous + 1)}>Bubble</button>
        <button onClick={event => {
          event.stopPropagation();
          setInner(previous => previous + 1);
        }}>Stop</button>
      </div>
      <p role="status">{outer}/{inner}</p>
    </main>
  );
}
