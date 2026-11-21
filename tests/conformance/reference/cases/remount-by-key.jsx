import { useState } from 'react';
import { KeyedRow } from './keyed-list.jsx';

export function RemountByKey() {
  const [version, setVersion] = useState(1);
  return (
    <main>
      <button onClick={() => setVersion(previous => previous + 1)}>New key</button>
      <ul><KeyedRow key={'version-' + version} name="item" /></ul>
    </main>
  );
}
