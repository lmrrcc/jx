import {useImperativeHandle, useRef, useState} from 'react';

function Field({id, prefix, ref}) {
  const input = useRef(null);
  useImperativeHandle(ref, () => ({
    id,
    prefix,
    fill(value) {
      input.current.value = prefix + ':' + value;
      input.current.focus();
    },
    read() { return input.current.value; }
  }), [id, prefix]);
  return <li data-row={id}><input ref={input} aria-label={'Handle ' + id} defaultValue={id + '-initial'} /></li>;
}

function describe(ref) {
  const handle = ref.current;
  return handle === null ? 'null' : handle.id + '/' + handle.prefix + '/' + handle.read();
}

export function CombinedImperativeHandles() {
  const primary = useRef(null);
  const alternate = useRef(null);
  const second = useRef(null);
  const rememberedA = useRef(null);
  const rememberedB = useRef(null);
  const [useAlternate, setUseAlternate] = useState(false);
  const [reversed, setReversed] = useState(false);
  const [showA, setShowA] = useState(true);
  const [mounted, setMounted] = useState(true);
  const [prefix, setPrefix] = useState('old');
  const [inspection, setInspection] = useState('uninspected');
  const activeA = useAlternate ? alternate : primary;
  const ids = reversed ? ['b', 'a'] : ['a', 'b'];
  return (
    <main>
      <p data-testid="handle-snapshot">{inspection}</p>
      <button onClick={() => {
        rememberedA.current = activeA.current;
        rememberedB.current = second.current;
        setInspection('remembered');
      }}>Remember handles</button>
      <button onClick={() => setInspection('primary=' + describe(primary) + ';alternate=' + describe(alternate)
        + ';b=' + describe(second) + ';same-a=' + (rememberedA.current === activeA.current)
        + ';same-b=' + (rememberedB.current === second.current))}>Inspect handles</button>
      <button onClick={() => activeA.current.fill('written-a')}>Fill a</button>
      <button onClick={() => second.current.fill('written-b')}>Fill b</button>
      <button onClick={() => setPrefix('new')}>Change prefixes</button>
      <button onClick={() => setUseAlternate(value => !value)}>Switch a ref</button>
      <button onClick={() => setReversed(value => !value)}>Reverse rows</button>
      <button onClick={() => setShowA(false)}>Remove a</button>
      <button onClick={() => setShowA(true)}>Restore a</button>
      <button onClick={() => setMounted(false)}>Clear rows</button>
      <ul>{mounted ? ids.filter(id => showA || id !== 'a')
        .map(id => <Field key={id} id={id} prefix={prefix} ref={id === 'a' ? activeA : second} />) : null}</ul>
    </main>
  );
}
