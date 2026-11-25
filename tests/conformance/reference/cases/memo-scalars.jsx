import {memo, useState} from 'react';
const shared = {number: 5};
const value = step => [7, 7, -0, 0, 0, NaN, NaN, 'x', 'x', shared, shared, {number: 5}, null, null][step];
const Row = memo(function Row({value}) {
  (globalThis.__benchLog ||= []).push('memo scalar render');
  return <p data-testid="memo-scalar">present</p>;
});
export function MemoScalars() {
  const [step, setStep] = useState(0);
  return <main>
    <button onClick={() => setStep(current => current + 1)}>Next scalar</button>
    <p role="status">{step}</p>
    <Row value={value(step)} />
  </main>;
}
