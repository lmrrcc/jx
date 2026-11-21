import { useFormStatus } from 'react-dom';

let release = () => {};

function Submit() {
  const { pending } = useFormStatus();
  return <button type="submit" disabled={pending}>{pending ? 'Sending' : 'Send'}</button>;
}

export function FormStatus() {
  return (
    <main>
      <form action={async () => { await new Promise(resolve => { release = resolve; }); }}>
        <Submit />
      </form>
      <button onClick={() => release()}>Release</button>
    </main>
  );
}
