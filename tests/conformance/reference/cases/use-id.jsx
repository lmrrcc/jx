import { useId } from 'react';

function IdField({ label }) {
  const id = useId();
  return (
    <p>
      <label htmlFor={id}>{label}</label>
      <input id={id} />
    </p>
  );
}

export function UseId() {
  return (
    <main>
      <IdField label="First" />
      <IdField label="Second" />
    </main>
  );
}
