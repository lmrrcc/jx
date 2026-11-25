import {Component, PureComponent, createContext, useState} from 'react';

const Theme = createContext('day');
const log = value => (globalThis.__benchLog ||= []).push(value);
const current = (peer, theme) => `${peer.props.revision}/${peer.state.count}/${theme}`;

function mounting(peer, id, variant, amount) {
  log(`${id}:mount:${variant}`);
  peer.setState(pending => ({count: pending.count + amount}),
    () => log(`${id}:mount-callback:${variant}:${peer.state.count}`));
}

function receiving(peer, id, next, theme, nextTheme, variant, amount) {
  log(`${id}:receive:${variant}:${current(peer, theme)}->${next.revision}/${nextTheme}`);
  if (next.revision !== peer.props.revision) {
    peer.setState(pending => ({count: pending.count + amount}),
      () => log(`${id}:receive-callback:${variant}:${peer.state.count}`));
  }
}

function updating(peer, id, next, state, theme, nextTheme, variant) {
  log(`${id}:update:${variant}:${current(peer, theme)}->${next.revision}/${state.count}/${nextTheme}`);
}

class AuthoredLegacy extends Component {
  static displayName = 'jx.conformance.cases.AuthoredLegacy';
  static contextType = Theme;
  static defaultProps = {label: 'default'};
  state = {count: 0};
  componentWillMount() { mounting(this, 'normal', 'old', 1); }
  UNSAFE_componentWillMount() { mounting(this, 'normal', 'unsafe', 10); }
  componentWillReceiveProps(next, context) {
    receiving(this, 'normal', next, this.context, context, 'old', 100);
  }
  UNSAFE_componentWillReceiveProps(next, context) {
    receiving(this, 'normal', next, this.context, context, 'unsafe', 1000);
  }
  componentWillUpdate(next, state, context) {
    updating(this, 'normal', next, state, this.context, context, 'old');
  }
  UNSAFE_componentWillUpdate(next, state, context) {
    updating(this, 'normal', next, state, this.context, context, 'unsafe');
  }
  render() {
    log('normal:render');
    return <output data-testid="authored-legacy-normal">{`${this.props.revision}/${this.state.count}/${this.context}/${this.props.label}`}</output>;
  }
}

class AuthoredLegacyPure extends PureComponent {
  static displayName = 'jx.conformance.cases.AuthoredLegacyPure';
  state = {count: 0};
  componentWillMount() { mounting(this, 'pure', 'old', 1); }
  UNSAFE_componentWillMount() { mounting(this, 'pure', 'unsafe', 10); }
  componentWillReceiveProps(next) {
    receiving(this, 'pure', next, 'none', 'none', 'old', 100);
  }
  UNSAFE_componentWillReceiveProps(next) {
    receiving(this, 'pure', next, 'none', 'none', 'unsafe', 1000);
  }
  componentWillUpdate(next, state) {
    updating(this, 'pure', next, state, 'none', 'none', 'old');
  }
  UNSAFE_componentWillUpdate(next, state) {
    updating(this, 'pure', next, state, 'none', 'none', 'unsafe');
  }
  render() {
    log('pure:render');
    return <output data-testid="authored-legacy-pure">{`${this.props.revision}/${this.state.count}`}</output>;
  }
}

export function ClassLegacyAuthoring() {
  const [revision, setRevision] = useState(0), [pulse, setPulse] = useState(0);
  const [theme, setTheme] = useState('day');
  return <main>
    <button onClick={() => setPulse(value => value + 1)}>Equal authored legacy props</button>
    <button onClick={() => setRevision(value => value + 1)}>Advance authored legacy props</button>
    <button onClick={() => setTheme('night')}>Change authored legacy context</button>
    <button onClick={() => setTheme(null)}>Null authored legacy context</button>
    <output data-testid="authored-legacy-pulse">{pulse}</output>
    <Theme value={theme}><AuthoredLegacy revision={revision}/></Theme>
    <AuthoredLegacyPure revision={revision}/>
  </main>;
}
