import { useState } from 'react';

export function KeyedRow({ name }) {
  const [count, setCount] = useState(0);
  return (
    <li data-row={name}>
      <span role="status">{count}</span>
      <button onClick={() => setCount(previous => previous + 1)}>Increment {name}</button>
    </li>
  );
}

export function KeyedList() {
  const [names, setNames] = useState(['a', 'b', 'c']);
  return (
    <main>
      <button onClick={() => setNames(previous => [...previous].reverse())}>Reverse</button>
      <button onClick={() => setNames(previous => previous.filter(name => name !== 'a'))}>Remove a</button>
      <button onClick={() => setNames(previous => previous.includes('a') ? previous : [...previous, 'a'])}>Restore a</button>
      <ul>
        {names.map(name => <KeyedRow key={name} name={name} />)}
      </ul>
    </main>
  );
}
