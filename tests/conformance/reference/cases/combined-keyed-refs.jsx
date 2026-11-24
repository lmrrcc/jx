import {useCallback, useLayoutEffect, useRef, useState} from 'react';
import {log} from '../bench.js';

function Row({id, revision}) {
  const attached = useRef(null);
  const callback = useCallback(node => {
    attached.current = node;
    log('attach ' + id + ' ' + revision + ' ' + node.tagName);
    return () => {
      log('detach ' + id + ' ' + revision);
      attached.current = null;
    };
  }, [id, revision]);
  useLayoutEffect(() => {
    log('layout ' + id + ' ' + revision + ' ' + (attached.current === null ? 'missing'
      : attached.current.tagName + ' ' + attached.current.value));
    return () => log('layout-cleanup ' + id + ' ' + revision);
  }, [id, revision]);
  return <li data-row={id}><input ref={callback} aria-label={'Ref ' + id} defaultValue={id + '-initial'} /></li>;
}

export function CombinedKeyedRefs() {
  const [reversed, setReversed] = useState(false);
  const [showA, setShowA] = useState(true);
  const [mounted, setMounted] = useState(true);
  const [revision, setRevision] = useState(0);
  const [tick, setTick] = useState(0);
  const ids = reversed ? ['b', 'a'] : ['a', 'b'];
  return (
    <main>
      <p data-testid="parent-tick">{tick}</p>
      <p data-testid="callback-revision">{revision}</p>
      <button onClick={() => setTick(value => value + 1)}>Rerender parent</button>
      <button onClick={() => setReversed(value => !value)}>Reverse rows</button>
      <button onClick={() => setRevision(value => value + 1)}>Replace callbacks</button>
      <button onClick={() => setShowA(false)}>Remove a</button>
      <button onClick={() => setShowA(true)}>Restore a</button>
      <button onClick={() => setMounted(false)}>Clear rows</button>
      <ul>{mounted ? ids.filter(id => showA || id !== 'a').map(id => <Row key={id} id={id} revision={revision} />) : null}</ul>
    </main>
  );
}
