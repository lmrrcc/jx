import {Component, createRef, useMemo, useState} from 'react';
import {log} from '../bench.js';

const FAILURE = new Error('class child failed');
const NATIVE_ERROR = new Error('foreign render');
const NATIVE_OBJECT = Object.freeze({message: 'foreign object', stack: new Error('foreign object').stack});
const label = error => {
  if (error === FAILURE) return 'java';
  const kind = error === null ? 'NULL' : error === undefined ? 'UNDEFINED' : 'OBJECT';
  const name = error === NATIVE_ERROR ? 'native-error' : error === NATIVE_OBJECT ? 'native-object'
    : error === null ? 'native-null' : error === undefined ? 'native-undefined' : 'unexpected';
  return name + ':' + kind;
};
class FailureBoundary extends Component {
  constructor(props) { super(props); this.state = {failure: null, failed: false, count: 0}; this.fallback = createRef(); }
  static getDerivedStateFromError(failure) { return {failure, failed: true}; }
  increment() { this.setState(state => ({count: state.count + 1})); }
  reset() { this.setState({failure: null, failed: false}); }
  componentDidCatch(error, info) {
    const committed = this.fallback.current !== null
      && this.fallback.current.textContent === this.props.id + ':fallback:' + this.state.count;
    log(this.props.id + ':caught:' + label(error) + ':same=' + Object.is(this.state.failure, error)
      + ':committed=' + committed + ':stack=' + (info.componentStack.length > 0));
  }
  render() {
    if (this.props.ownFailure) throw FAILURE;
    const body = !this.state.failed ? this.props.child
      : <output data-testid={this.props.id + '-fallback'} ref={this.fallback}>{this.props.id + ':fallback:' + this.state.count}</output>;
    return <section data-testid={this.props.id + '-boundary'}>
      <output data-testid={this.props.id + '-count'}>{this.state.count}</output>{body}
    </section>;
  }
}
function NativeFailure({phase}) {
  if (phase === 'native-error') throw NATIVE_ERROR;
  if (phase === 'native-object') throw NATIVE_OBJECT;
  if (phase === 'native-null') throw null;
  if (phase === 'native-undefined') throw undefined;
  throw new Error('Invalid foreign throw mode');
}
class FailureLeaf extends Component {
  state = {count: 0};
  static getDerivedStateFromProps(props) { if (props.phase === 'derive') throw FAILURE; return null; }
  componentDidUpdate() { if (this.props.phase === 'commit') throw FAILURE; }
  render() {
    if (this.props.phase === 'render') throw FAILURE;
    if (this.props.phase.startsWith('native-')) return <NativeFailure phase={this.props.phase} />;
    return <div data-testid="failure-leaf">
      <output data-testid="failure-leaf-count">{this.state.count}</output>
      <button onClick={() => this.setState(state => ({count: state.count + 1}))}>Increment leaf</button>
      <button onClick={() => { throw FAILURE; }}>Throw event</button>
    </div>;
  }
}
export function ClassErrorLifecycle() {
  const outer = useMemo(createRef, []), inner = useMemo(createRef, []);
  const [phase, setPhase] = useState('ok'), [key, setKey] = useState(0);
  const child = <FailureLeaf phase={phase} key="leaf" />;
  const nested = <FailureBoundary id="inner" child={child} ownFailure={phase === 'own'} key="inner" ref={inner} />;
  return <main>
    <output data-testid="error-sibling">unaffected</output>
    <button onClick={() => inner.current.increment()}>Increment boundary</button>
    <button onClick={() => setPhase('render')}>Fail render</button>
    <button onClick={() => setPhase('derive')}>Fail derive</button>
    <button onClick={() => setPhase('commit')}>Fail commit</button>
    <button onClick={() => setPhase('native-error')}>Fail native error</button>
    <button onClick={() => setPhase('native-object')}>Fail native object</button>
    <button onClick={() => setPhase('native-null')}>Fail native null</button>
    <button onClick={() => setPhase('native-undefined')}>Fail native undefined</button>
    <button onClick={() => setPhase('own')}>Fail own boundary</button>
    <button onClick={() => { setPhase('ok'); outer.current.reset(); if (inner.current) inner.current.reset(); }}>Reset errors</button>
    <button onClick={() => setKey(value => value + 1)}>Remount boundaries</button>
    <FailureBoundary id="outer" child={nested} ownFailure={false} key={'outer-' + key} ref={outer} />
  </main>;
}
