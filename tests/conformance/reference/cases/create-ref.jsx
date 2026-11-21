import { createRef } from 'react';

const field = createRef();

export function CreateRef() {
  return (
    <main>
      <input ref={field} aria-label="Created" />
      <button onClick={() => field.current.focus()}>Focus field</button>
    </main>
  );
}
