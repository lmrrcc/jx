import { Profiler, useState } from 'react';
import { log } from '../bench.js';

export function ProfilerCase() {
  const [count, setCount] = useState(0);
  return (
    <main>
      <Profiler id="bench" onRender={(id, phase) => log(id + ' ' + phase)}>
        <p role="status">{count}</p>
      </Profiler>
      <button onClick={() => setCount(previous => previous + 1)}>Update</button>
    </main>
  );
}
