import { useImperativeHandle, useRef, useState } from 'react';

function Field({ ref }) {
  const input = useRef(null);
  useImperativeHandle(ref, () => ({
    fill(value) {
      input.current.value = value;
      input.current.focus();
    },
    read() {
      return input.current.value;
    }
  }), []);
  return <input ref={input} aria-label="Inner" />;
}

export function ImperativeHandle() {
  const field = useRef(null);
  const [read, setRead] = useState('');
  return (
    <main>
      <Field ref={field} />
      <button onClick={() => {
        field.current.fill('filled');
        setRead(field.current.read());
      }}>Fill through handle</button>
      <p role="status">{read}</p>
    </main>
  );
}
