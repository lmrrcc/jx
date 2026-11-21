import { useReducer } from 'react';
import { log } from '../bench.js';

function initialize(seed) {
  log('init');
  return seed * 2;
}

export function ReducerLazyInit() {
  const [count, dispatch] = useReducer((state, step) => state + step, 5, initialize);
  return (
    <main>
      <p role="status">{count}</p>
      <button onClick={() => dispatch(1)}>Increment</button>
    </main>
  );
}
