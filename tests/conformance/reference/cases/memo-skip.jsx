import { memo, useState } from 'react';
import { log } from '../bench.js';

const Row = memo(function Row({ label }) {
  log('render ' + label);
  return <p data-testid="row">{label}</p>;
});

const Compared = memo(function Compared({ label, tick }) {
  log('render compared ' + label);
  return <p data-testid="compared">{label} {tick}</p>;
}, (previous, next) => previous.label === next.label);

export function MemoSkip() {
  const [tick, setTick] = useState(0);
  const [label, setLabel] = useState('a');
  return (
    <main>
      <Row label={label} />
      <Compared label={label} tick={tick} />
      <p role="status">{tick}</p>
      <button onClick={() => setTick(previous => previous + 1)}>Rerender parent</button>
      <button onClick={() => setLabel('b')}>Change label</button>
    </main>
  );
}
