import {Children, useState} from 'react';

export function MappedChild({id}) {
  const [count, setCount] = useState(0);
  const [draft, setDraft] = useState('');
  return <section data-child={id}>
    <button onClick={() => setCount(value => value + 1)}>Increment {id}</button>
    <output>{count}</output>
    <input aria-label={'Draft ' + id} value={draft} onChange={event => setDraft(event.target.value)} />
  </section>;
}

function MapChildren({shell, copies, children}) {
  return <div data-testid="mapped">{Children.map(children, child => copies
    ? [<article key="left" data-copy="left">{child}</article>, <article key="right" data-copy="right">{child}</article>]
    : <article key={shell}>{child}</article>)}</div>;
}

export function CombinedChildrenMap() {
  const [reversed, setReversed] = useState(false);
  const [removed, setRemoved] = useState(false);
  const [copies, setCopies] = useState(false);
  const [shell, setShell] = useState(null);
  const ids = reversed ? ['b', 'a'] : ['a', 'b'];
  return <main>
    <button onClick={() => setReversed(value => !value)}>Reverse</button>
    <button onClick={() => setRemoved(value => !value)}>Toggle a</button>
    <button onClick={() => setShell('shell/changed')}>Change wrapper key</button>
    <button onClick={() => setCopies(value => !value)}>Toggle copies</button>
    <MapChildren shell={shell} copies={copies}>
      {ids.filter(id => !removed || id !== 'a').map(id => <MappedChild key={id + '/=:'} id={id} />)}
    </MapChildren>
  </main>;
}

function ListChildren({children}) {
  return <div data-testid="listed">{Children.toArray(children)}</div>;
}

export function CombinedChildrenList() {
  const [reversed, setReversed] = useState(false);
  const ids = reversed ? ['b', 'a'] : ['a', 'b'];
  return <main>
    <button onClick={() => setReversed(value => !value)}>Reverse first group</button>
    <ListChildren>{[
      ids.map(id => <MappedChild key={id} id={'first-' + id} />),
      [<MappedChild key="a" id="second-a" />]
    ]}</ListChildren>
  </main>;
}
