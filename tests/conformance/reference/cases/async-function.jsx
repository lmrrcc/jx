import { useState } from 'react';

let release = () => {};
let fail = () => {};
function gate() {
  return new Promise((resolve, reject) => { release = resolve; fail = reject; });
}

export function AsyncFunction() {
  const [lines, setLines] = useState([]);
  const add = line => setLines(all => [...all, line]);
  async function load() {
    add('start');
    const first = await gate();
    add('first ' + first);
    try {
      await gate();
    } catch (error) {
      add('caught ' + error.message);
    }
    return 'finished';
  }
  return (
    <main>
      <button onClick={() => { load().then(add); add('returned'); }}>Run</button>
      <button onClick={() => release('one')}>Release</button>
      <button onClick={() => fail(new Error('broken'))}>Fail</button>
      <ol>{lines.map((line, index) => <li key={index}>{line}</li>)}</ol>
    </main>
  );
}
