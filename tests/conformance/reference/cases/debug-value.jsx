import { useDebugValue, useState } from 'react';

function useLabelled(initial) {
  const [value, setValue] = useState(initial);
  useDebugValue(value, current => 'value: ' + current);
  return [value, setValue];
}

export function DebugValue() {
  const [value, setValue] = useLabelled(1);
  return (
    <main>
      <p role="status">{value}</p>
      <button onClick={() => setValue(previous => previous + 1)}>Increment</button>
    </main>
  );
}
