import { useState } from 'react';

function Counter({ label }) {
  const [count, setCount] = useState(0);
  return <button onClick={() => setCount(previous => previous + 1)}>{label} {count}</button>;
}

// A null child and a nested list each hold one position among their siblings, so a
// sibling written after them keeps its state when they appear or grow.
export function ChildSlots() {
  const [shown, setShown] = useState(false);
  const [items, setItems] = useState(['a']);
  return (
    <main>
      <div>
        {shown ? <p>banner</p> : null}
        <Counter label="after-conditional" />
      </div>
      <div>
        {items.map(item => <span key={item}>{item}</span>)}
        <Counter label="after-list" />
      </div>
      <button onClick={() => setShown(true)}>Show banner</button>
      <button onClick={() => setItems(['a', 'b'])}>Add item</button>
    </main>
  );
}
