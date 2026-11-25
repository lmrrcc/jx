import {Component, PureComponent, createRef, useRef, useState} from 'react';
import {log} from '../bench.js';

const text = state => state.count + ':' + (state.note === null ? '<null>' : state.note);

// Render logs are test instrumentation only, matching the Java consumer.
class Counter extends Component {
  state = {count: 0, note: 'ready'};
  output = createRef();

  addThree() {
    this.setState(pending => ({count: pending.count + 1}));
    this.setState(pending => ({count: pending.count + 1}));
    this.setState(pending => ({count: pending.count + 1}), this.callback);
    log('normal:handler:' + this.state.count);
  }
  clearNote() { this.setState({note: null}, this.callback); }
  emptyPatch() { this.setState({}, this.callback); }
  nullUpdate() { this.setState(() => null, this.callback); }
  hideOutput() { this.setState({note: 'hidden'}); }
  showOutput() { this.setState({note: 'ready'}); }
  domText() { return this.output.current === null ? '<none>' : this.output.current.textContent; }
  callback = () => log('normal:callback:' + this.state.count + ':' + this.domText());
  shouldComponentUpdate(nextProps) { return nextProps.allowRender; }
  componentDidMount() { log('normal:mount'); }
  componentWillUnmount() { log('normal:unmount'); }
  getSnapshotBeforeUpdate() { return this.domText(); }
  componentDidUpdate(previousProps, previousState, snapshot) {
    log('normal:commit:' + snapshot + '->' + this.domText());
  }
  render() {
    log('normal:render');
    return this.state.note === 'hidden' ? null
      : <output data-testid="normal-value" ref={this.output}>{text(this.state)}</output>;
  }
}

class PureCounter extends PureComponent {
  state = {count: 0, note: 'ready'};
  output = createRef();

  emptyPatch() { this.setState({}, this.callback); }
  sameState() { this.setState({count: this.state.count}, this.callback); }
  addOne() { this.setState(pending => ({count: pending.count + 1}), this.callback); }
  callback = () => log('pure:callback:' + this.state.count + ':' + this.output.current.textContent);
  componentDidMount() { log('pure:mount'); }
  componentWillUnmount() { log('pure:unmount'); }
  getSnapshotBeforeUpdate() { return this.output.current.textContent; }
  componentDidUpdate(previousProps, previousState, snapshot) {
    log('pure:commit:' + snapshot + '->' + this.output.current.textContent);
  }
  render() {
    log('pure:render');
    return <output data-testid="pure-value" ref={this.output}>{text(this.state)}</output>;
  }
}

export function ClassRuntime() {
  const normal = useRef(null);
  const pure = useRef(null);
  const [, setTick] = useState(0);
  const [settings, setSettings] = useState({theme: 'dark'});
  const [number, setNumber] = useState(0);
  const [allowRender, setAllowRender] = useState(true);
  const [mounted, setMounted] = useState(true);
  const [key, setKey] = useState(0);
  const [inspection, setInspection] = useState('uninspected');
  const [snapshotIdentity, setSnapshotIdentity] = useState('uninspected');
  const props = {label: 'Counter', settings, number, allowRender};
  return <main>
    <button disabled={!mounted} onClick={() => normal.current.addThree()}>Add three</button>
    <button disabled={!mounted} onClick={() => normal.current.clearNote()}>Clear note</button>
    <button disabled={!mounted} onClick={() => normal.current.emptyPatch()}>Empty patch</button>
    <button disabled={!mounted} onClick={() => normal.current.nullUpdate()}>Null update</button>
    <button disabled={!mounted} onClick={() => normal.current.hideOutput()}>Hide normal output</button>
    <button disabled={!mounted} onClick={() => normal.current.showOutput()}>Show normal output</button>
    <button onClick={() => pure.current.emptyPatch()}>Pure empty patch</button>
    <button onClick={() => pure.current.sameState()}>Pure same state</button>
    <button onClick={() => pure.current.addOne()}>Pure add one</button>
    <button onClick={() => setTick(previous => previous + 1)}>Equal props</button>
    <button onClick={() => setSettings({theme: 'dark'})}>New nested value</button>
    <button onClick={() => setNumber(NaN)}>NaN prop</button>
    <button onClick={() => setNumber(-0)}>Negative zero prop</button>
    <button onClick={() => setNumber(0)}>Positive zero prop</button>
    <button onClick={() => setAllowRender(false)}>Block normal</button>
    <button onClick={() => setAllowRender(true)}>Allow normal</button>
    <button onClick={() => {
      const peer = normal.current;
      setInspection(peer === null ? 'detached'
        : 'count=' + peer.state.count + ';note=' + (peer.state.note === null ? '<null>' : peer.state.note)
          + ';allow=' + peer.props.allowRender);
    }}>Inspect instance</button>
    <button disabled={!mounted} onClick={() => {
      const peer = normal.current;
      setSnapshotIdentity('props=' + (peer.props === peer.props ? 'same' : 'different')
        + ';state=' + (peer.state === peer.state ? 'same' : 'different'));
    }}>Inspect snapshot identity</button>
    <button disabled={!mounted} onClick={() => normal.current.forceUpdate()}>Force normal</button>
    <button disabled={!mounted} onClick={() => setKey(previous => previous + 1)}>Remount normal</button>
    <button disabled={!mounted} onClick={() => setMounted(false)}>Unmount normal</button>
    <output data-testid="instance-read">{inspection}</output>
    <output data-testid="snapshot-identity">{snapshotIdentity}</output>
    {mounted ? <Counter {...props} key={'normal-' + key} ref={normal} /> : null}
    <PureCounter {...props} key="pure" ref={pure} />
  </main>;
}
