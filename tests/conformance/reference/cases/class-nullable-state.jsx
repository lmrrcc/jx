import {Component, PureComponent, cloneElement, useCallback, useRef, useState} from 'react';
import {log} from '../bench.js';

const field = (state, name) => !Object.hasOwn(state, name) ? 'absent' : state[name] === undefined ? 'undefined' : state[name] === null ? 'null' : String(state[name]);
const summary = state => state === null ? 'null' : 'count=' + field(state, 'count') + ';note=' + field(state, 'note');
function operate(peer, operation) {
  const before = peer.state;
  const committed = () => log(peer.props.id + ':callback:' + operation + ':' + summary(peer.state) + ':same=' + (peer.state === before));
  switch (operation) {
    case 'direct': peer.setState(null, committed); break;
    case 'functional': peer.setState(pending => { log(peer.props.id + ':updater:' + summary(pending)); return null; }, committed); break;
    case 'empty': peer.setState({}, committed); break;
    case 'partial': peer.setState({count: 1}, committed); break;
    case 'undefined': peer.setState({note: undefined}, committed); break;
    case 'null-field': peer.setState({note: null}, committed); break;
    case 'queued':
      peer.setState(pending => { log(peer.props.id + ':updater:first:' + summary(pending)); return {count: (pending?.count ?? 0) + 1}; });
      peer.setState(pending => { log(peer.props.id + ':updater:second:' + summary(pending)); return {count: (pending?.count ?? 0) + 1}; }, committed);
      break;
    default: throw new Error('Unknown state operation');
  }
  log(peer.props.id + ':handler:' + operation + ':' + summary(peer.state) + ':same=' + (peer.state === before));
}
function output(peer) {
  log(peer.props.id + ':render');
  return <output data-testid={'nullable-' + peer.props.id}>{summary(peer.state)}</output>;
}
class NullableNormal extends Component {
  constructor(props) { super(props); this.state = props.id === 'seed' ? {note: undefined} : null; }
  render() {
    const rendered = output(this);
    return this.props.id === 'error' && this.props.revision > 0 && this.state?.note !== 'caught'
      ? <>{rendered}<NullableFailure /></> : rendered;
  }
  shouldComponentUpdate(next, pending) { log(this.props.id + ':should:' + summary(this.state) + '->' + summary(pending)); return !next.blocked; }
  getSnapshotBeforeUpdate(previous, state) { return summary(state); }
  componentDidUpdate(previous, state, snapshot) { log(this.props.id + ':commit:' + summary(state) + '->' + summary(this.state) + ':snapshot=' + snapshot); }
  componentDidCatch(error, info) { log(this.props.id + ':caught:' + error.message + ':state=' + summary(this.state) + ':stack=' + (info.componentStack.length > 0)); }
}
class NullablePure extends PureComponent {
  constructor(props) { super(props); this.state = null; }
  render() { return output(this); }
  componentDidUpdate(previous, state) { log(this.props.id + ':commit:' + summary(state) + '->' + summary(this.state)); }
}
function derive(props, pending) {
  log(props.id + ':derive:' + props.revision + ':' + summary(pending));
  switch (props.revision) {
    case 1: return {count: 10};
    case 2: return {note: null};
    case 3: return {note: undefined};
    default: return null;
  }
}
class NullableDerivedNormal extends NullableNormal { static getDerivedStateFromProps = derive; }
class NullableDerivedPure extends NullablePure { static getDerivedStateFromProps = derive; }
function NullableFailure() { throw new Error('nullable child failed'); }
class NullableErrorBoundary extends NullableNormal { static getDerivedStateFromError() { return {note: 'caught'}; } }
function ClassStateObserver({element}) {
  const original = element.props.ref, id = element.props.id;
  const ref = useCallback(instance => {
    const owners = globalThis.__nullableClassOwners || (globalThis.__nullableClassOwners = new Map());
    if (instance === null) owners.delete(id); else owners.set(id, instance);
    if (original) original.current = instance;
  }, [original, id]);
  return cloneElement(element, {ref});
}
export function ClassNullableState() {
  const normal = useRef(null), blocked = useRef(null), pure = useRef(null), seed = useRef(null);
  const [key, setKey] = useState(0);
  const controls = [];
  for (const [id, ref] of [['normal', normal], ['pure', pure], ['blocked', blocked]])
    for (const operation of ['direct', 'functional', 'empty', 'partial', 'undefined', 'null-field', 'queued'])
      controls.push(<button key={id + ':' + operation} onClick={() => operate(ref.current, operation)}>{id + ' ' + operation}</button>);
  return <main>{controls}<button onClick={() => setKey(value => value + 1)}>Remount nullable classes</button>
    <ClassStateObserver element={<NullableNormal id="normal" blocked={false} revision={0} key={'normal-' + key} ref={normal}/>} />
    <ClassStateObserver element={<NullablePure id="pure" blocked={false} revision={0} key={'pure-' + key} ref={pure}/>} />
    <ClassStateObserver element={<NullableNormal id="blocked" blocked={true} revision={0} key={'blocked-' + key} ref={blocked}/>} />
    <ClassStateObserver element={<NullableNormal id="seed" blocked={false} revision={0} key={'seed-' + key} ref={seed}/>} />
  </main>;
}
export function ClassNullableDerived() {
  const normal = useRef(null), blocked = useRef(null), pure = useRef(null), error = useRef(null);
  const [revision, setRevision] = useState(0), [fail, setFail] = useState(false);
  const controls = [];
  for (const [id, ref] of [['normal', normal], ['pure', pure], ['blocked', blocked]])
    for (const operation of ['direct', 'functional', 'empty', 'partial', 'undefined', 'null-field', 'queued'])
      controls.push(<button key={id + ':' + operation} onClick={() => operate(ref.current, operation)}>{id + ' ' + operation}</button>);
  return <main>{controls}
    <button onClick={() => setRevision(value => value + 1)}>Advance nullable derivation</button>
    <button onClick={() => setFail(true)}>Fail nullable child</button>
    <output data-testid="nullable-revision">{revision}</output>
    <ClassStateObserver element={<NullableDerivedNormal id="normal" blocked={false} revision={revision} key="normal" ref={normal}/>} />
    <ClassStateObserver element={<NullableDerivedPure id="pure" blocked={false} revision={revision} key="pure" ref={pure}/>} />
    <ClassStateObserver element={<NullableDerivedNormal id="blocked" blocked={true} revision={revision} key="blocked" ref={blocked}/>} />
    <ClassStateObserver element={<NullableErrorBoundary id="error" blocked={false} revision={fail ? 1 : 0} key="error" ref={error}/>} />
  </main>;
}
