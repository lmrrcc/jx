import {Component, PureComponent, createRef, useMemo, useState} from 'react';
import {log} from '../bench.js';

const initial = () => ({revision: -1, count: 1, note: 'initial', edits: 0});
const summary = state => state.count + ':' + state.edits + ':' + (state.note === null ? '<null>' : state.note);
const value = peer => peer.props.id + ':' + summary(peer.state) + ':born=' + peer.born;
const derive = (props, state) => {
  if (props.empty) return {};
  if (props.revision !== state.revision) {
    const patch = {revision: props.revision, count: props.base};
    return props.clear ? {...patch, note: null} : patch;
  }
  return state.count > props.base + 5 ? {count: props.base + 5} : null;
};
const increment = (state, amount) => ({count: state.count + amount, edits: state.edits + 1});
class Normal extends Component {
  static getDerivedStateFromProps = derive;
  constructor(props) { super(props); this.state = initial(); this.born = this.state.count; this.output = createRef(); }
  addTwo() {
    this.setState(state => increment(state, 1));
    this.setState(state => increment(state, 1), () => log(this.props.id + ':callback:' + summary(this.state)));
  }
  addHundred() { this.setState(state => increment(state, 100), () => log(this.props.id + ':callback:' + summary(this.state))); }
  shouldComponentUpdate(props) { return !props.blocked; }
  componentDidMount() { log(this.props.id + ':mount:' + summary(this.state)); }
  getSnapshotBeforeUpdate() { return this.output.current.textContent; }
  componentDidUpdate(props, state, snapshot) { log(this.props.id + ':commit:' + snapshot + '->' + value(this)); }
  render() { log(this.props.id + ':render'); return <output data-testid={'derived-' + this.props.id} ref={this.output}>{value(this)}</output>; }
}
class Pure extends PureComponent {
  static getDerivedStateFromProps = derive;
  constructor(props) { super(props); this.state = initial(); this.born = this.state.count; }
  addTwo() {
    this.setState(state => increment(state, 1));
    this.setState(state => increment(state, 1), () => log(this.props.id + ':callback:' + summary(this.state)));
  }
  addHundred() { this.setState(state => increment(state, 100), () => log(this.props.id + ':callback:' + summary(this.state))); }
  componentDidMount() { log(this.props.id + ':mount:' + summary(this.state)); }
  componentDidUpdate() { log('pure:commit:' + summary(this.state)); }
  render() { log('pure:render'); return <output data-testid="derived-pure">{value(this)}</output>; }
}
export function ClassDerivedProps() {
  const normal = useMemo(createRef, []), pure = useMemo(createRef, []), blocked = useMemo(createRef, []);
  const [revision, setRevision] = useState(0), [pulse, setPulse] = useState(0);
  const [empty, setEmpty] = useState(false), [inspection, setInspection] = useState('uninspected');
  const base = (revision + 1) * 10;
  return <main>
    <button onClick={() => { normal.current.addTwo(); pure.current.addTwo(); blocked.current.addTwo(); }}>Add two to classes</button>
    <button onClick={() => { normal.current.addHundred(); pure.current.addHundred(); blocked.current.addHundred(); }}>Add hundred to classes</button>
    <button onClick={() => setRevision(value => value + 1)}>Advance derived props</button>
    <button onClick={() => setPulse(value => value + 1)}>Rerender equal props</button>
    <button onClick={() => setEmpty(true)}>Use empty derived patch</button>
    <button onClick={() => blocked.current.forceUpdate()}>Force blocked class</button>
    <button onClick={() => setInspection('normal=' + summary(normal.current.state)
      + ';pure=' + summary(pure.current.state) + ';blocked=' + summary(blocked.current.state))}>Inspect derived state</button>
    <output data-testid="derived-pulse">{pulse}</output>
    <output data-testid="derived-inspection">{inspection}</output>
    <Normal id="normal" revision={revision} base={base} clear={revision >= 2} blocked={false} empty={empty} key="normal" ref={normal} />
    <Pure id="pure" revision={revision} base={base} clear={revision >= 2} blocked={false} empty={empty} key="pure" ref={pure} />
    <Normal id="blocked" revision={revision} base={base} clear={revision >= 2} blocked={true} empty={empty} key="blocked" ref={blocked} />
  </main>;
}
