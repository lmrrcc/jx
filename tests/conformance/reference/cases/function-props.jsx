import { useState } from 'react';

function Stepper({ value, onStep, renderValue }) {
  return (
    <section aria-label="Stepper">
      {renderValue(value)}
      <button onClick={() => onStep()}>Step</button>
    </section>
  );
}

export function FunctionProps() {
  const [count, setCount] = useState(0);
  const [strong, setStrong] = useState(false);
  return (
    <main>
      <Stepper value={count}
               onStep={() => setCount(previous => previous + 1)}
               renderValue={value => strong
                 ? <strong data-testid="rendered">value {value}</strong>
                 : <em data-testid="rendered">value {value}</em>} />
      <button onClick={() => setStrong(true)}>Use strong</button>
    </main>
  );
}
