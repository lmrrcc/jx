import {Component, PureComponent, cloneElement, useCallback, useRef} from 'react';
import {log} from '../bench.js';

const field = (state, name) => !Object.hasOwn(state, name) ? 'absent'
  : state[name] === undefined ? 'undefined' : state[name] === null ? 'null' : String(state[name]);
const summary = state => state === null ? 'null' : 'count=' + field(state, 'count') + ';note=' + field(state, 'note');

function operate(peer, operation) {
  const before = peer.state;
  const committed = () => log(peer.props.id + ':callback:' + operation + ':' + summary(peer.state) + ':same=' + (peer.state === before));
  switch (operation) {
    case 'empty': peer.setState({}, committed); break;
    case 'partial': peer.setState({count: 1}, committed); break;
    case 'undefined': peer.setState({note: undefined}, committed); break;
    case 'null-field': peer.setState({note: null}, committed); break;
    case 'queued':
      peer.setState(pending => {
        log(peer.props.id + ':updater:first:' + summary(pending));
        return {count: (pending?.count ?? 0) + 1};
      });
      peer.setState(pending => {
        log(peer.props.id + ':updater:second:' + summary(pending));
        return {count: (pending?.count ?? 0) + 1};
      }, committed);
      break;
    default: throw new Error('Unknown sparse state operation');
  }
  log(peer.props.id + ':handler:' + operation + ':' + summary(peer.state) + ':same=' + (peer.state === before));
}

function output(peer) {
  log(peer.props.id + ':render');
  return <output data-testid={'sparse-' + peer.props.id}>{summary(peer.state)}</output>;
}
function committed(peer, previous, snapshot) {
  log(peer.props.id + ':commit:' + summary(previous) + '->' + summary(peer.state) + ':snapshot=' + snapshot);
}
class AuthoredSparseNormal extends Component {
  constructor(props) { super(props); this.state = null; }
  render() { return output(this); }
  getSnapshotBeforeUpdate(previousProps, previousState) { return summary(previousState); }
  componentDidUpdate(previousProps, previousState, snapshot) { committed(this, previousState, snapshot); }
}
class AuthoredSparsePure extends PureComponent {
  constructor(props) { super(props); this.state = null; }
  render() { return output(this); }
  getSnapshotBeforeUpdate(previousProps, previousState) { return summary(previousState); }
  componentDidUpdate(previousProps, previousState, snapshot) { committed(this, previousState, snapshot); }
}
function ClassStateObserver({element}) {
  const original = element.props.ref, id = element.props.id;
  const ref = useCallback(instance => {
    const owners = globalThis.__nullableClassOwners || (globalThis.__nullableClassOwners = new Map());
    if (instance === null) owners.delete(id); else owners.set(id, instance);
    if (original) original.current = instance;
  }, [original, id]);
  return cloneElement(element, {ref});
}

export function ClassSparseAuthoring() {
  const normal = useRef(null), pure = useRef(null);
  const controls = [];
  for (const [id, ref] of [['normal', normal], ['pure', pure]])
    for (const operation of ['empty', 'partial', 'undefined', 'null-field', 'queued'])
      controls.push(<button key={id + ':' + operation} onClick={() => operate(ref.current, operation)}>{id + ' ' + operation}</button>);
  return <main>{controls}
    <ClassStateObserver element={<AuthoredSparseNormal id="normal" ref={normal}/>} />
    <ClassStateObserver element={<AuthoredSparsePure id="pure" ref={pure}/>} />
  </main>;
}
