import { useEffect, useState } from 'react';
import { log } from '../bench.js';

function useCounter(name, step) {
  const [value, setValue] = useState(0);
  useEffect(() => {
    log(name + ' ' + value);
  }, [value]);
  return { value, advance: () => setValue(previous => previous + step) };
}

export function CustomHook() {
  const ones = useCounter('ones', 1);
  const tens = useCounter('tens', 10);
  return (
    <main>
      <p data-testid="ones">{ones.value}</p>
      <p data-testid="tens">{tens.value}</p>
      <button onClick={() => ones.advance()}>Advance ones</button>
      <button onClick={() => tens.advance()}>Advance tens</button>
    </main>
  );
}
