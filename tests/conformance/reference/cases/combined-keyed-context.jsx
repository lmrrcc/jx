import {createContext, memo, useContext, useEffect, useState} from 'react';
import {log} from '../bench.js';

const Theme = createContext('outside');

const Row = memo(function Row({id, label}) {
  const theme = useContext(Theme);
  const [count, setCount] = useState(0);
  const [draft, setDraft] = useState('');
  log('render ' + id + ' ' + label + ' ' + theme);
  useEffect(() => {
    log('setup ' + id + ' ' + theme);
    return () => log('cleanup ' + id + ' ' + theme);
  }, [theme]);
  return <li data-row={id} data-theme={theme}>
    <h2>{label + ' ' + id}</h2>
    <p data-testid={'theme-' + id}>{theme}</p>
    <span role="status">{count}</span>
    <input aria-label={'Draft ' + id} value={draft} onChange={event => setDraft(event.target.value)} />
    <button onClick={() => setCount(previous => previous + 1)}>Increment {id}</button>
  </li>;
});

export function CombinedKeyedContext() {
  const [reversed, setReversed] = useState(false);
  const [showA, setShowA] = useState(true);
  const [theme, setTheme] = useState('light');
  const [label, setLabel] = useState('Row');
  const [tick, setTick] = useState(0);
  const ids = reversed ? ['b', 'a'] : ['a', 'b'];
  return <main>
    <h1>Combined keyed context</h1>
    <p data-testid="parent-tick">{tick}</p>
    <button onClick={() => setTick(previous => previous + 1)}>Rerender parent</button>
    <button onClick={() => setLabel('Updated')}>Change labels</button>
    <button onClick={() => setReversed(previous => !previous)}>Reverse rows</button>
    <button onClick={() => setShowA(false)}>Remove a</button>
    <button onClick={() => setShowA(true)}>Restore a</button>
    <button onClick={() => setTheme(previous => previous === 'light' ? 'dark' : 'light')}>Change theme</button>
    <Theme value={theme}>
      <ul>{ids.filter(id => showA || id !== 'a').map(id => <Row key={id} id={id} label={label} />)}</ul>
    </Theme>
  </main>;
}
