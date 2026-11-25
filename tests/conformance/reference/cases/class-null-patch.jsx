import {Component, createRef} from 'react';
import {log} from '../bench.js';

class NullPatchCounter extends Component {
  constructor(props) { super(props); this.state = {count: 0, note: 'ready'}; this.output = createRef(); }
  value() { return this.state.count + ':' + (this.state.note === null ? '<null>' : this.state.note); }
  completed(operation, previous) {
    log('null-patch:callback:' + operation + ':' + this.value() + ':same=' + (this.state === previous) + ':dom=' + this.output.current.textContent);
  }
  directNull() {
    const previous = this.state;
    this.setState(null);
    this.setState(null, () => this.completed('direct', previous));
    log('null-patch:handler:direct:' + this.value() + ':same=' + (this.state === previous));
  }
  functionalNull() {
    const previous = this.state;
    this.setState(pending => { log('null-patch:updater:' + pending.count); return null; }, () => this.completed('functional', previous));
    log('null-patch:handler:functional:' + this.value() + ':same=' + (this.state === previous));
  }
  emptyPatch() { const previous = this.state; this.setState({}, () => this.completed('empty', previous)); }
  mixedQueue() {
    const previous = this.state;
    this.setState(null, () => this.completed('first', previous));
    this.setState(pending => ({count: pending.count + 1}), () => this.completed('middle', previous));
    this.setState(null, () => this.completed('last', previous));
    log('null-patch:handler:mixed:' + this.value());
  }
  clearField() { const previous = this.state; this.setState({note: null}, () => this.completed('field', previous)); }
  shouldComponentUpdate(next, pending) { log('null-patch:should:' + this.state.count + '->' + pending.count); return true; }
  componentDidUpdate(previous, old) { log('null-patch:commit:' + old.count + '->' + this.state.count); }
  render() {
    log('null-patch:render');
    return <section>
      <output ref={this.output} data-testid="null-patch-value">{this.value()}</output>
      <button onClick={() => this.directNull()}>Direct null patch</button>
      <button onClick={() => this.functionalNull()}>Functional null patch</button>
      <button onClick={() => this.emptyPatch()}>Empty state patch</button>
      <button onClick={() => this.mixedQueue()}>Mixed null queue</button>
      <button onClick={() => this.clearField()}>Clear nullable field</button>
    </section>;
  }
}

export function ClassNullPatch() { return <NullPatchCounter />; }
