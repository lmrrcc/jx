import {useState} from 'react';
import {preload} from 'react-dom';

const STYLE = 'data:text/css,.jx-resource-hydration{}';

export function CombinedResourceHydration() {
  const [count, setCount] = useState(0);
  preload(STYLE, {as: 'style'});
  preload(STYLE, {as: 'style'});
  return <main>
    <h1>Hydrated resource</h1>
    <p role="status">{count}</p>
    <button onClick={() => setCount(previous => previous + 1)}>Increment</button>
  </main>;
}
