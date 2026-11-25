import {Component, createContext, createRef, useMemo, useState} from 'react';
import {log} from '../bench.js';

const DEFAULT = {name: 'default'}, LIGHT = {name: 'light'}, DARK = {name: 'dark'};
const Theme = createContext(DEFAULT);
const name = value => value === null ? '<null>' : value.name;
const values = (props, state, context) => props.revision + '/' + state.count + '/' + name(context);

class Counter extends Component {
  static contextType = Theme;
  constructor(props, context) {
    super(props, context);
    this.state = {count: 0};
    this.born = name(context);
  }
  shouldComponentUpdate(nextProps, nextState, nextContext) {
    log('policy:' + this.props.id + ':old=' + this.current() + ':next='
      + values(nextProps, nextState, nextContext) + ':identity=' + (nextContext === nextProps.expected));
    return nextProps.allow;
  }
  add() {
    this.setState(pending => ({count: pending.count + 1}), () => log(this.prefix() + ':callback:' + this.current()));
    log(this.prefix() + ':handler:' + this.current());
  }
  prefix() { return this.props.id === 'authored' ? 'authored-policy' : 'policy'; }
  force() { this.forceUpdate(() => log(this.prefix() + ':forced:' + this.current())); }
  current() { return values(this.props, this.state, this.context); }
  componentDidMount() { log('policy:mount:' + this.props.id); }
  componentDidUpdate(oldProps, oldState) {
    log('policy:commit:' + this.props.id + ':previous=' + oldProps.revision + '/' + oldState.count + ':current=' + this.current());
  }
  render() { return <output data-testid={'policy-' + this.props.id}>{this.current() + ':born=' + this.born}</output>; }
}

export function ClassNextContext() {
  const counter = useMemo(createRef, []);
  const authored = useMemo(createRef, []);
  const [revision, setRevision] = useState(0);
  const [theme, setTheme] = useState(LIGHT);
  const [allow, setAllow] = useState(false);
  const [inspection, setInspection] = useState('uninspected');
  const [authoredInspection, setAuthoredInspection] = useState('uninspected');
  return <main>
    <button onClick={() => { counter.current.add(); authored.current.add(); }}>Increment context-policy state</button>
    <button onClick={() => setRevision(value => value + 1)}>Change context-policy props</button>
    <button onClick={() => { counter.current.force(); authored.current.force(); }}>Force context-policy</button>
    <button onClick={() => setTheme(DARK)}>Dark context-policy</button>
    <button onClick={() => setTheme(null)}>Null context-policy</button>
    <button onClick={() => setAllow(true)}>Allow context-policy</button>
    <button onClick={() => { setRevision(value => value + 1); setTheme(DARK); counter.current.add(); authored.current.add(); }}>Combined context-policy update</button>
    <button onClick={() => { setInspection(counter.current.current()); setAuthoredInspection(authored.current.current()); }}>Inspect context-policy</button>
    <output data-testid="policy-revision">{revision}</output>
    <output data-testid="policy-allow">{allow ? 'yes' : 'no'}</output>
    <output data-testid="policy-inspection">{inspection}</output>
    <output data-testid="authored-policy-inspection">{authoredInspection}</output>
    <Counter id="default" revision={0} allow={false} expected={DEFAULT} key="default" />
    <Theme.Provider value={theme}>
      <Counter ref={counter} id="provided" revision={revision} allow={allow} expected={theme} key="provided" />
      <Counter ref={authored} id="authored" revision={revision} allow={allow} expected={theme} key="authored" />
    </Theme.Provider>
    <Theme.Provider value={null}><Counter id="null" revision={0} allow={false} expected={null} key="null" /></Theme.Provider>
  </main>;
}
