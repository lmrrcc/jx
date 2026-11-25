import {PureComponent, createRef, useEffect, useMemo, useRef, useState} from 'react';
import {log} from '../bench.js';

class KeyedCounter extends PureComponent {
  state = {count: 0};

  addOne() { this.setState(previous => ({count: previous.count + 1})); }
  componentDidMount() { log(this.props.id + ':mount'); }
  componentWillUnmount() { log(this.props.id + ':unmount'); }
  render() {
    // Render logging is test instrumentation, matching the Java consumer.
    log(this.props.id + ':render');
    return <output data-testid={'class-' + this.props.id.toLowerCase()}>
      {this.props.id + ':' + this.state.count}
    </output>;
  }
}

export function ClassKeyed() {
  const a = useMemo(createRef, []);
  const b = useMemo(createRef, []);
  const originals = useRef(null);
  const [reversed, setReversed] = useState(false);
  const [inspection, setInspection] = useState('uninspected');
  useEffect(() => {
    if (originals.current === null) originals.current = {a: a.current, b: b.current};
  }, []);
  const ids = reversed ? ['B', 'A'] : ['A', 'B'];
  return <main>
    <button onClick={() => setReversed(previous => !previous)}>Reverse classes</button>
    <button onClick={() => a.current.addOne()}>Increment A</button>
    <button onClick={() => b.current.addOne()}>Increment B</button>
    <button onClick={() => setInspection(
      'A=' + (a.current === originals.current?.a ? 'same' : 'different')
      + ';B=' + (b.current === originals.current?.b ? 'same' : 'different')
    )}>Inspect class refs</button>
    <output data-testid="class-refs">{inspection}</output>
    <section data-testid="class-order">
      {ids.map(id => <KeyedCounter key={id.toLowerCase()} id={id} ref={id === 'A' ? a : b} />)}
    </section>
  </main>;
}
