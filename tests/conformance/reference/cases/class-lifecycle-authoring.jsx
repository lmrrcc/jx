import {PureComponent, createContext, createRef, useMemo, useRef, useState} from 'react';
import {log} from '../bench.js';

const themeContext = createContext('default');
const failure = new Error('authored lifecycle child failed');

function AuthoredLifecycleChild({fail}) {
  const [count, setCount] = useState(0);
  if (fail) throw failure;
  return <button data-testid="authored-lifecycle-child" onClick={() => setCount(value => value + 1)}>Child: {count}</button>;
}

class AuthoredLifecycle extends PureComponent {
  static contextType = themeContext;
  constructor(props, context) {
    super(props, context);
    this.state = {revision: -1, count: -1, note: 'ready', failed: false, error: null};
    this.fallback = createRef();
  }
  static getDerivedStateFromProps(next, pending) {
    if (next.revision === pending.revision) return null;
    return {revision: next.revision, count: next.revision * 10, note: next.revision === 0 ? 'ready' : null};
  }
  static getDerivedStateFromError(error) { return {failed: true, error}; }
  componentDidCatch(error, info) {
    log('authored-lifecycle:caught:same=' + (error === this.state.error)
      + ':original=' + (error === failure)
      + ':committed=' + (this.fallback.current !== null && this.fallback.current.textContent === this.summary())
      + ':stack=' + (info.componentStack.length > 0));
  }
  recover() { this.setState({failed: false, error: null}); }
  summary() { return (this.context === null ? '<null>' : this.context) + ':' + this.state.count + ':' + (this.state.note === null ? '<null>' : this.state.note); }
  render() {
    log('authored-lifecycle:render');
    return <section data-testid="authored-lifecycle-boundary">
      <output data-testid="authored-lifecycle-value">{this.summary()}</output>
      <button onClick={() => this.setState(pending => ({count: pending.count + 1}))}>Increment lifecycle boundary</button>
      {this.state.failed
        ? <output ref={this.fallback} data-testid="authored-lifecycle-fallback">{this.summary()}</output>
        : <AuthoredLifecycleChild fail={this.props.fail} />}
    </section>;
  }
}

export function ClassLifecycleAuthoring() {
  const [theme, setTheme] = useState('day');
  const [revision, setRevision] = useState(0);
  const [fail, setFail] = useState(false);
  const [pulse, setPulse] = useState(0);
  const [identity, setIdentity] = useState('uninspected');
  const beforeFailure = useRef(null);
  const boundary = useMemo(createRef, []);
  return <main>
    <button onClick={() => setTheme('night')}>Change lifecycle context</button>
    <button onClick={() => setTheme(null)}>Null lifecycle context</button>
    <button onClick={() => setRevision(value => value + 1)}>Advance lifecycle revision</button>
    <button onClick={() => setPulse(value => value + 1)}>Equal lifecycle props</button>
    <button onClick={() => { beforeFailure.current = boundary.current; setFail(true); }}>Fail authored child</button>
    <button onClick={() => { setFail(false); boundary.current.recover(); }}>Recover authored child</button>
    <button onClick={() => setIdentity(boundary.current !== null && boundary.current === beforeFailure.current ? 'same' : 'different')}>Inspect lifecycle identity</button>
    <output data-testid="authored-lifecycle-pulse">{pulse}</output>
    <output data-testid="authored-lifecycle-identity">{identity}</output>
    <themeContext.Provider value={theme}><AuthoredLifecycle ref={boundary} revision={revision} fail={fail} /></themeContext.Provider>
  </main>;
}
