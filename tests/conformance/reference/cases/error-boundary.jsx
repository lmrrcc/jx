import { Component, useState } from 'react';

class Boundary extends Component {
  state = { error: null };
  static getDerivedStateFromError(error) { return { error }; }
  render() {
    return this.state.error
      ? <p role="alert">failed: {this.state.error.message}</p>
      : this.props.children;
  }
}

function Exploder() {
  const [broken, setBroken] = useState(false);
  if (broken) throw new Error('render exploded');
  return <button onClick={() => setBroken(true)}>Explode</button>;
}

export function ErrorBoundaryCase() {
  return (
    <main>
      <p data-testid="sibling">sibling</p>
      <Boundary><Exploder /></Boundary>
    </main>
  );
}
