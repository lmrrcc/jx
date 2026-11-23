import { useFormState } from 'react-dom';

export function FormState() {
  // The deprecated name of useActionState.
  const [total, formAction] = useFormState((previous, formData) => previous + Number(formData.get('step')), 10);
  return (
    <main>
      <form action={formAction}>
        <input name="step" defaultValue="3" aria-label="Step" />
        <button type="submit">Add</button>
      </form>
      <p role="status">{total}</p>
    </main>
  );
}
