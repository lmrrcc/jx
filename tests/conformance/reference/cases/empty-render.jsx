import {useState, useEffect} from 'react';
const log = message => (globalThis.__benchLog ||= []).push(message);
function Child({visible}) {
  const [count, setCount] = useState(0);
  useEffect(() => { log('empty mount'); return () => log('empty unmount'); }, []);
  if (!visible) return null;
  return <button onClick={() => setCount(value => value + 1)}>Child {count}</button>;
}
export function EmptyRender() {
  const [visible, setVisible] = useState(true);
  const [mounted, setMounted] = useState(true);
  return <main>
    <button onClick={() => setVisible(value => !value)}>Toggle output</button>
    <button onClick={() => setMounted(value => !value)}>Toggle owner</button>
    {mounted ? <Child visible={visible} /> : null}
    <button data-testid="following">Following sibling</button>
  </main>;
}
