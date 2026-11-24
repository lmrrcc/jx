// No case import is allowed here. The route is a separate esbuild entry, fetched on demand.
import {Component, StrictMode, Suspense, lazy, useState} from 'react';
import {createRoot} from 'react-dom/client';
import {caseId} from './bench.js';

const load = () => import('./route.js').then(module => ({default: module.route()}));
let Route = lazy(load);

class Boundary extends Component {
  state = {error: null};
  static getDerivedStateFromError(error) { return {error}; }
  render() {
    if (!this.state.error) return this.props.children;
    return <div data-testid="bench-route-error">
      <p>{this.state.error.message}</p>
      <button data-testid="bench-route-retry" onClick={this.props.retry}>Retry route</button>
    </div>;
  }
}

function Host() {
  const [opened, setOpened] = useState(false);
  const [attempt, setAttempt] = useState(0);
  if (!opened) return <button data-testid="bench-open-route" onClick={() => setOpened(true)}>Open route</button>;
  const route = <Suspense fallback={<p data-testid="bench-route-loading">Loading route</p>}><Route /></Suspense>;
  // This case deliberately tests the root's uncaught-error policy.
  if (caseId() === 'error-reporting') return route;
  return <div key={attempt}><Boundary retry={() => {
    Route = lazy(load);
    setAttempt(previous => previous + 1);
  }}>{route}</Boundary></div>;
}

createRoot(document.getElementById('app')).render(
  caseId() === 'strict-nested' ? <Host /> : <StrictMode><Host /></StrictMode>);
