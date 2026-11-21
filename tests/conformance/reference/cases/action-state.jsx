import { useActionState } from 'react';

export function ActionState() {
  const [total, formAction, isPending] = useActionState(
    async (previous, formData) => previous + Number(formData.get('step')), 0);
  return (
    <main>
      <form action={formAction}>
        <input name="step" defaultValue="2" aria-label="Step" />
        <button type="submit">Add</button>
      </form>
      <p role="status">{isPending ? 'pending' : total}</p>
    </main>
  );
}
