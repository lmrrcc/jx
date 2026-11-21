import { useRef } from 'react';

function Field({ label, ref }) {
  return <input ref={ref} aria-label={label} />;
}

export function RefAsProp() {
  const field = useRef(null);
  return (
    <main>
      <Field ref={field} label="Direct" />
      <button onClick={() => field.current.focus()}>Focus field</button>
    </main>
  );
}
