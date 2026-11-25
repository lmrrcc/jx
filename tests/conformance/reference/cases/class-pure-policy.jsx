import {PureComponent, createContext, createRef, useState} from 'react';
import {log} from '../bench.js';

const Theme = createContext('day');
class Base extends PureComponent {
  static contextType = Theme;
  state = {count: 0};
  output = createRef();
  value() { return this.context + ':' + this.props.revision + ':' + this.state.count + ':' + this.props.allow; }
  increment() {
    this.setState(pending => ({count: pending.count + 1}),
      () => log('pure-callback:' + this.id + ':' + this.value() + ':dom=' + this.output.current.textContent));
  }
  render() {
    log('pure-render:' + this.id);
    return <section>
      <output ref={this.output} data-testid={this.id}>{this.value()}</output>
      <button onClick={() => this.increment()}>Increment {this.id}</button>
      <button onClick={() => this.forceUpdate()}>Force {this.id}</button>
    </section>;
  }
}
class Legacy extends Base {
  id = 'legacy';
  shouldComponentUpdate(next, pending) {
    log('pure-policy:legacy:' + this.props.revision + '->' + next.revision);
    return next.allow;
  }
}
class Contextual extends Base {
  id = 'context';
  shouldComponentUpdate(next, pending, context) {
    log('pure-policy:context:' + this.context + '->' + context);
    return next.allow;
  }
}
export function ClassPurePolicy() {
  const [pulse, setPulse] = useState(0), [revision, setRevision] = useState(0);
  const [allow, setAllow] = useState(true), [theme, setTheme] = useState('day');
  return <main>
    <output data-testid="pulse">{pulse}</output>
    <button onClick={() => setPulse(value => value + 1)}>Same props</button>
    <button onClick={() => setAllow(false)}>Block policy</button>
    <button onClick={() => setAllow(true)}>Allow policy</button>
    <button onClick={() => setRevision(value => value + 1)}>Advance props</button>
    <button onClick={() => setTheme('night')}>Change theme</button>
    <Theme.Provider value={theme}>
      <Legacy revision={revision} allow={allow} /><Contextual revision={revision} allow={allow} />
    </Theme.Provider>
  </main>;
}
