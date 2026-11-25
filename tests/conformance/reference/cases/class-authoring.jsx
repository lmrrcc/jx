import {Component, PureComponent, createRef, useMemo, useState} from 'react';
import {log} from '../bench.js';

class AuthoredCounter extends Component {
  constructor(props) {
    super(props);
    this.state = {count: props.initial, note: 'ready'};
    this.output = createRef();
  }
  addThree() {
    this.setState(previous => ({count: previous.count + 1}));
    this.setState(previous => ({count: previous.count + 1}));
    this.setState(previous => ({count: previous.count + 1}), () => log('authored:callback:' + this.state.count));
    log('authored:handler:' + this.state.count);
  }
  clearNote() { this.setState({note: null}); }
  componentDidMount() { log('authored:mount'); }
  componentWillUnmount() { log('authored:unmount'); }
  getSnapshotBeforeUpdate() { return this.output.current.textContent; }
  componentDidUpdate(previousProps, previousState, snapshot) {
    log('authored:commit:' + snapshot + '->' + this.output.current.textContent);
  }
  render() {
    // Render logging is test instrumentation, matching the authored Java consumer.
    log('authored:render');
    return <section>
      <output data-testid="authored-value" ref={this.output}>
        {this.props.label + ':' + this.state.count + ':' + (this.state.note === null ? '<null>' : this.state.note)}
      </output>
      <button onClick={() => this.addThree()}>Authored add three</button>
    </section>;
  }
}

class AuthoredPure extends PureComponent {
  constructor(props) {
    super(props);
    this.state = {count: 0};
  }
  componentDidMount() { log('authored-pure:mount'); }
  componentWillUnmount() { log('authored-pure:unmount'); }
  render() {
    log('authored-pure:render');
    return <section>
      <output data-testid="authored-pure-value">{this.props.label + ':' + this.state.count}</output>
      <button onClick={() => this.setState(previous => ({count: previous.count + 1}))}>Authored pure add</button>
    </section>;
  }
}

export function ClassAuthoring() {
  const normal = useMemo(createRef, []);
  const pure = useMemo(createRef, []);
  const [tick, setTick] = useState(0);
  const [key, setKey] = useState(0);
  const [mounted, setMounted] = useState(true);
  const [inspection, setInspection] = useState('uninspected');
  return <main>
    <button onClick={() => setTick(previous => previous + 1)}>Equal authored props</button>
    <button disabled={!mounted} onClick={() => normal.current.clearNote()}>Clear authored note</button>
    <button disabled={!mounted} onClick={() => setKey(previous => previous + 1)}>Remount authored</button>
    <button disabled={!mounted} onClick={() => setMounted(false)}>Unmount authored</button>
    <button onClick={() => setInspection(normal.current === null ? 'detached' : 'count=' + normal.current.state.count)}>Inspect authored ref</button>
    <output data-testid="authored-pulse">{tick}</output>
    <output data-testid="authored-inspection">{inspection}</output>
    {mounted ? <AuthoredCounter label="Count" initial={2} key={'normal-' + key} ref={normal} /> : null}
    <AuthoredPure {...{label: 'Pure'}} ref={pure} key="pure" />
  </main>;
}
