import { useReducer } from 'react';

export function ReducerBatch() {
  const [text, dispatch] = useReducer((state, action) => state + action, '');
  return (
    <main>
      <p role="status">{text}</p>
      <button onClick={() => {
        dispatch('a');
        dispatch('b');
      }}>Dispatch a then b</button>
    </main>
  );
}
