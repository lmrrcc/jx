import { startTransition } from 'react';
import { requestFormReset } from 'react-dom';

export function FormReset() {
  return (
    <main>
      <form onSubmit={event => {
        event.preventDefault();
        const form = event.currentTarget;
        startTransition(() => requestFormReset(form));
      }}>
        <input name="note" defaultValue="start" aria-label="Note" />
        <button type="submit">Reset form</button>
      </form>
    </main>
  );
}
