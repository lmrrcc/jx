import { useState } from 'react';

export function UncontrolledInputs() {
  const [renders, setRenders] = useState(0);
  return (
    <main>
      <input aria-label="Free text" defaultValue="initial" />
      <input type="checkbox" aria-label="Option" defaultChecked />
      <select aria-label="Choice" defaultValue="b">
        <option value="a">A</option>
        <option value="b">B</option>
      </select>
      <button onClick={() => setRenders(previous => previous + 1)}>Rerender</button>
      <p role="status">{renders}</p>
    </main>
  );
}
