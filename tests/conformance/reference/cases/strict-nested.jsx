import { StrictMode, useEffect, useState } from 'react';
import { log } from '../bench.js';

function Probe({ name }) {
  log('render ' + name);
  useEffect(() => {
    log(name + ' setup');
    return () => log(name + ' cleanup');
  }, []);
  return <p>{name}</p>;
}

// Mounted without a root StrictMode, so only the nested parts are checked.
export function StrictNested() {
  const [shown, setShown] = useState(false);
  return (
    <main>
      <Probe name="plain" />
      <StrictMode>
        <Probe name="checked" />
      </StrictMode>
      {shown ? <StrictMode><Probe name="late" /></StrictMode> : null}
      <button onClick={() => setShown(true)}>Show late</button>
    </main>
  );
}
