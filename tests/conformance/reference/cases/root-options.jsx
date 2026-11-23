import { Component, startTransition, useId, useState } from 'react';
import { log } from '../bench.js';

// Options for createRoot; the entry mounts this case with them.
export const rootOptions = {
  identifierPrefix: 'bench-',
  onCaughtError: (error, info) => log('caught ' + error.message + (info.componentStack ? ' with stack' : '')),
  onUncaughtError: error => log('uncaught ' + error.message),
  // React wraps the error it recovered from; the original is the cause.
  onRecoverableError: error => log('recoverable ' + (error.cause ?? error).message)
};

class Boundary extends Component {
  state = { error: null };
  static getDerivedStateFromError(error) { return { error }; }
  render() {
    return this.state.error ? <p role="alert">failed</p> : this.props.children;
  }
}

function Exploder() {
  const [broken, setBroken] = useState(false);
  if (broken) throw new Error('child exploded');
  return <button onClick={() => setBroken(true)}>Explode child</button>;
}

let thrown = false;
// Fails the first time it renders armed; React recovers by rendering again.
function Flaky({ armed }) {
  if (armed && !thrown) {
    thrown = true;
    throw new Error('flaky once');
  }
  return <p data-testid="flaky">{armed ? 'recovered' : 'calm'}</p>;
}

export function RootOptions() {
  const id = useId();
  const [armed, setArmed] = useState(false);
  const [broken, setBroken] = useState(false);
  if (broken) throw new Error('root exploded');
  return (
    <main>
      <p data-testid="id">{id}</p>
      <Boundary><Exploder /></Boundary>
      <Flaky armed={armed} />
      <button onClick={() => startTransition(() => setArmed(true))}>Arm</button>
      <button onClick={() => setBroken(true)}>Break root</button>
    </main>
  );
}
