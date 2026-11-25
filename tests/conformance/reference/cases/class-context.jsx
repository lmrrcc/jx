import {Activity, Component, PureComponent, createContext, createRef, useMemo, useRef, useState} from 'react';
import {log} from '../bench.js';

const DEFAULT = {name: 'default'};
const OUTER = {name: 'outer'};
const INNER = {name: 'inner'};
const THEME = createContext(DEFAULT);
const name = theme => theme === null ? '<null>' : theme.name;
const lifecycle = (peer, phase) => log(peer.props.id + ':' + phase + ':' + name(peer.context) + ':' + peer.state.count);
const value = peer => {
  log(peer.props.id + ':render');
  return <output data-testid={'context-' + peer.props.id}>
    {peer.props.id + ':' + name(peer.context) + ':' + peer.state.count + ':born=' + peer.born}
  </output>;
};

class Normal extends Component {
  static contextType = THEME;
  constructor(props, context) {
    super(props, context);
    this.state = {count: 0};
    this.born = name(this.context);
  }
  addOne() { this.setState(previous => ({count: previous.count + 1}), () => log(this.props.id + ':callback:' + this.state.count)); }
  same(theme) { return this.context === theme; }
  shouldComponentUpdate() { return false; }
  componentDidMount() { lifecycle(this, 'mount'); }
  componentDidUpdate() { lifecycle(this, 'update'); }
  componentWillUnmount() { lifecycle(this, 'unmount'); }
  render() { return value(this); }
}

class Pure extends PureComponent {
  static contextType = THEME;
  constructor(props, context) {
    super(props, context);
    this.state = {count: 0};
    this.born = name(this.context);
  }
  addOne() { this.setState(previous => ({count: previous.count + 1}), () => log(this.props.id + ':callback:' + this.state.count)); }
  same(theme) { return this.context === theme; }
  describe() { return name(this.context) + ';count=' + this.state.count; }
  componentDidMount() { lifecycle(this, 'mount'); }
  componentDidUpdate() { lifecycle(this, 'update'); }
  componentWillUnmount() { lifecycle(this, 'unmount'); }
  render() { return value(this); }
}
const matched = same => same ? 'same' : 'different';

export function ClassContextCase() {
  const normal = useMemo(createRef, []);
  const pure = useMemo(createRef, []);
  const fallback = useMemo(createRef, []);
  const nested = useMemo(createRef, []);
  const activity = useMemo(createRef, []);
  const retained = useRef(null);
  const [theme, setTheme] = useState(OUTER);
  const [pulse, setPulse] = useState(0);
  const [visible, setVisible] = useState(true);
  const [mounted, setMounted] = useState(true);
  const [inspection, setInspection] = useState('uninspected');
  const [retainedRead, setRetainedRead] = useState('uninspected');
  return <main>
    <button disabled={!mounted} onClick={() => normal.current.addOne()}>Increment blocked class</button>
    <button disabled={!mounted} onClick={() => pure.current.addOne()}>Increment pure class</button>
    <button onClick={() => setPulse(previous => previous + 1)}>Rerender context host</button>
    <button onClick={() => setTheme({name: 'outer'})}>Replace equal context</button>
    <button onClick={() => setTheme({name: 'dark'})}>Set dark context</button>
    <button onClick={() => setTheme(null)}>Set null context</button>
    <button disabled={!mounted || !visible} onClick={() => setInspection(
      'normal=' + matched(normal.current.same(theme)) + ';pure=' + matched(pure.current.same(theme))
      + ';default=' + matched(fallback.current.same(DEFAULT)) + ';nested=' + matched(nested.current.same(INNER))
      + ';activity=' + matched(activity.current.same(theme))
    )}>Inspect context identity</button>
    <button disabled={!mounted || !visible} onClick={() => {
      retained.current = activity.current;
      setVisible(false);
    }}>Hide activity</button>
    <button disabled={!mounted || visible} onClick={() => setVisible(true)}>Show activity</button>
    <button disabled={!mounted} onClick={() => retained.current.addOne()}>Increment retained activity</button>
    <button onClick={() => setRetainedRead(retained.current.describe())}>Inspect retained activity</button>
    <button disabled={!mounted} onClick={() => setMounted(false)}>Unmount contextual rows</button>
    <output data-testid="context-pulse">{pulse}</output>
    <output data-testid="context-inspection">{inspection}</output>
    <output data-testid="retained-context">{retainedRead}</output>
    <Pure id="default" key="default" ref={fallback} />
    {mounted ? <THEME.Provider value={theme}>
      <Normal id="normal" key="normal" ref={normal} />
      <Pure id="pure" key="pure" ref={pure} />
      <THEME.Provider value={INNER}><Pure id="nested" key="nested" ref={nested} /></THEME.Provider>
      <Activity mode={visible ? 'visible' : 'hidden'}><Pure id="activity" key="activity" ref={activity} /></Activity>
    </THEME.Provider> : null}
  </main>;
}
