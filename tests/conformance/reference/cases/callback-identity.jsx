import { useCallback, useRef, useState } from 'react';

export function CallbackIdentity() {
  const [noise, setNoise] = useState(0);
  const [dependency, setDependency] = useState(0);
  const stable = useCallback(() => 'stable', []);
  const dependent = useCallback(() => 'dependent', [dependency]);
  const firstStable = useRef(stable);
  const firstDependent = useRef(dependent);
  return (
    <main>
      <p data-testid="stable">{firstStable.current === stable ? 'same' : 'changed'}</p>
      <p data-testid="dependent">{firstDependent.current === dependent ? 'same' : 'changed'}</p>
      <p role="status">{noise}</p>
      <button onClick={() => setNoise(previous => previous + 1)}>Unrelated render</button>
      <button onClick={() => setDependency(previous => previous + 1)}>Change dependency</button>
    </main>
  );
}
