import {Component, PureComponent, Suspense, createContext, use, useMemo, useRef, useState} from 'react';
const Theme = createContext('day');
const log = value => (globalThis.__benchLog ||= []).push(value);
const value = peer => `${peer.props.phase}/${peer.state.count}/${peer.context}`;
function mounting(peer, variant, amount) {
  log(`${peer.props.id}:mount:${variant}:current=${value(peer)}`);
  peer.setState(pending => ({count: pending.count + amount}),
    () => log(`${peer.props.id}:mount-callback:${variant}:${peer.state.count}`));
}
function receiving(peer, next, context, variant, amount) {
  log(`${peer.props.id}:receive:${variant}:current=${value(peer)}:next=${next.phase}/${context}`);
  if (next.phase !== peer.props.phase) peer.setState(pending => ({count: pending.count + amount}),
    () => log(`${peer.props.id}:receive-callback:${variant}:${peer.state.count}`));
}
function updating(peer, next, state, context, variant) {
  log(`${peer.props.id}:update:${variant}:current=${value(peer)}:next=${next.phase}/${state.count}/${context}`);
}
function output(peer) {
  log(`${peer.props.id}:render:${value(peer)}`);
  return <output data-testid={peer.props.id}>{value(peer)}</output>;
}
class LegacyNormal extends Component {
  static contextType = Theme;
  state = {count: 0};
  componentWillMount() { mounting(this, 'old', 1); }
  UNSAFE_componentWillMount() { mounting(this, 'unsafe', 10); }
  componentWillReceiveProps(next, context) { receiving(this, next, context, 'old', 100); }
  UNSAFE_componentWillReceiveProps(next, context) { receiving(this, next, context, 'unsafe', 1000); }
  componentWillUpdate(next, state, context) { updating(this, next, state, context, 'old'); }
  UNSAFE_componentWillUpdate(next, state, context) { updating(this, next, state, context, 'unsafe'); }
  shouldComponentUpdate(next) { return next.allow; }
  componentDidMount() { log(`${this.props.id}:mounted`); }
  componentDidUpdate() { log(`${this.props.id}:committed`); }
  render() { return output(this); }
}
class LegacyPure extends PureComponent {
  static contextType = Theme;
  state = {count: 0};
  componentWillMount() { mounting(this, 'old', 1); }
  UNSAFE_componentWillMount() { mounting(this, 'unsafe', 10); }
  componentWillReceiveProps(next, context) { receiving(this, next, context, 'old', 100); }
  UNSAFE_componentWillReceiveProps(next, context) { receiving(this, next, context, 'unsafe', 1000); }
  componentWillUpdate(next, state, context) { updating(this, next, state, context, 'old'); }
  UNSAFE_componentWillUpdate(next, state, context) { updating(this, next, state, context, 'unsafe'); }
  componentDidMount() { log(`${this.props.id}:mounted`); }
  componentDidUpdate() { log(`${this.props.id}:committed`); }
  render() { return output(this); }
}
export function ClassLegacy() {
  const [phase, setPhase] = useState(0), [pulse, setPulse] = useState(0), [allow, setAllow] = useState(true);
  const [theme, setTheme] = useState('day'), [inspection, inspect] = useState('uninspected');
  const normal = useRef(null);
  return <main>
    <button onClick={() => setPulse(value => value + 1)}>Equal legacy props</button>
    <button onClick={() => setPhase(value => value + 1)}>Advance legacy props</button>
    <button onClick={() => setAllow(false)}>Block legacy normal</button>
    <button onClick={() => normal.current.forceUpdate()}>Force legacy normal</button>
    <button onClick={() => setTheme('night')}>Change legacy context</button>
    <button onClick={() => inspect(value(normal.current))}>Inspect legacy normal</button>
    <output data-testid="legacy-pulse">{pulse}</output>
    <output data-testid="legacy-inspection">{inspection}</output>
    <Theme value={theme}><LegacyNormal id="normal" phase={phase} allow={allow} key="normal" ref={normal}/><LegacyPure id="pure" phase={phase} allow={true} key="pure"/></Theme>
  </main>;
}
class LegacyDerived extends LegacyNormal {
  static getDerivedStateFromProps() { return null; }
}
class LegacySnapshot extends LegacyNormal {
  getSnapshotBeforeUpdate() { log(`${this.props.id}:snapshot`); return null; }
}
class LegacyErrorOnly extends LegacyNormal {
  static getDerivedStateFromError() { return null; }
}
export function ClassLegacySuppression() {
  const [phase, setPhase] = useState(0);
  return <main>
    <button onClick={() => setPhase(value => value + 1)}>Advance suppressed props</button>
    <Theme value="day">
      <LegacyDerived id="derived" phase={phase} allow={true} key="derived"/>
      <LegacySnapshot id="snapshot" phase={phase} allow={true} key="snapshot"/>
      <LegacyErrorOnly id="error" phase={phase} allow={true} key="error"/>
    </Theme>
  </main>;
}

function attempt() {
  let release;
  const result = new Promise(resolve => { release = () => resolve('ready'); });
  return {result, release};
}
function LegacyGate({id, count, result}) {
  return <output data-testid={'attempt-' + id}>{`${id}:${count}:${use(result)}`}</output>;
}
class LegacyInterrupted extends Component {
  state = {count: 0};
  queue(variant, amount) {
    log(`${this.props.id}:attempt-mount:${variant}:${this.state.count}`);
    this.setState(pending => ({count: pending.count + amount}),
      () => log(`${this.props.id}:attempt-callback:${variant}:${this.state.count}`));
  }
  componentWillMount() { this.queue('old', 1); }
  UNSAFE_componentWillMount() { this.queue('unsafe', 10); }
  componentDidMount() { log(`${this.props.id}:attempt-mounted`); }
  componentWillUnmount() { log(`${this.props.id}:attempt-unmounted`); }
  render() {
    log(`${this.props.id}:attempt-render:${this.state.count}`);
    return <LegacyGate id={this.props.id} count={this.state.count} result={this.props.attempt.result}/>;
  }
}
export function ClassLegacyInterrupted() {
  const owner = useMemo(() => ({first: attempt(), second: attempt()}), []), ref = useRef(null);
  const [selected, select] = useState('none'), [inspection, inspect] = useState('uninspected');
  return <main>
    <button onClick={() => select('A')}>Start attempt A</button>
    <button onClick={() => select('B')}>Start attempt B</button>
    <button onClick={() => select('none')}>Abandon attempt</button>
    <button onClick={owner.first.release}>Release attempt A</button>
    <button onClick={owner.second.release}>Release attempt B</button>
    <button onClick={() => inspect(ref.current === null ? 'detached' : ref.current.props.id)}>Inspect attempt</button>
    <output data-testid="attempt-inspection">{inspection}</output>
    <Suspense fallback={<p data-testid="attempt-waiting">waiting</p>}>
      {selected !== 'none' && <LegacyInterrupted id={selected} attempt={selected === 'A' ? owner.first : owner.second} key={selected} ref={ref}/>}
    </Suspense>
  </main>;
}
