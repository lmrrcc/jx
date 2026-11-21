import { useImperativeHandle, useRef, useState } from 'react';

function Field({ ref }) {
  const input = useRef(null);
  useImperativeHandle(ref, () => ({
    fill(value) {
      input.current.value = value;
      input.current.focus();
    }
  }), []);
  return <input ref={input} aria-label="Inner" />;
}

export function ImperativeHandle() {
  const field = useRef(null);
  const [keys, setKeys] = useState('');
  return (
    <main>
      <Field ref={field} />
      <button onClick={() => {
        field.current.fill('filled');
        setKeys(Object.keys(field.current).join(','));
      }}>Fill through handle</button>
      <p role="status">{keys}</p>
    </main>
  );
}
