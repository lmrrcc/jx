import { forwardRef, useRef } from 'react';

const Field = forwardRef(function Field({ label }, ref) {
  return <input ref={ref} aria-label={label} />;
});

export function ForwardRef() {
  const field = useRef(null);
  return (
    <main>
      <Field ref={field} label="Forwarded" />
      <button onClick={() => field.current.focus()}>Focus field</button>
    </main>
  );
}
