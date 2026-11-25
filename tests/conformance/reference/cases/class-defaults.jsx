import {Component, PureComponent, createElement, cloneElement, createRef, useMemo, useRef, useState} from 'react';

const DEFAULT_MARKER = Object.freeze({name: 'default'}), OTHER_MARKER = Object.freeze({name: 'other'});
const presence = (props, name) => !Object.prototype.hasOwnProperty.call(props, name) ? 'absent' : String(props[name]);
const summary = props => props.required + '|' + props.label + '|' + props.amount + '|' + props.enabled + '|' + props.text
  + '|' + (props.marker === DEFAULT_MARKER ? 'default' : 'other') + '|' + presence(props, 'nothing') + '|' + presence(props, 'unknown');
class DefaultedClass extends PureComponent {
  static defaultProps = Object.freeze(Object.assign(Object.create(null), {
    label: 'fallback', amount: 7, enabled: true, text: 'fallback-text', marker: DEFAULT_MARKER, nothing: null, unknown: undefined
  }));
  constructor(props) { super(props); this.state = {clicks: 0, derived: 'initial'}; this.constructed = summary(props); }
  static getDerivedStateFromProps(props) { return {derived: summary(props)}; }
  render() {
    return <section data-testid="defaulted-class">
      <output data-testid="default-values">{summary(this.props)}</output>
      <p data-testid="default-derived">{this.state.derived}</p>
      <p data-testid="default-constructor">{this.constructed}</p>
      <button onClick={() => this.setState(pending => ({clicks: pending.clicks + 1}))}>{'Default clicks: ' + this.state.clicks}</button>
    </section>;
  }
}
function description(mode, reset, ref) {
  let input = {required: 'required', key: reset ? 'replacement' : 'stable', ref};
  if (mode === 1 || mode >= 4) Object.assign(input, {label: undefined, amount: undefined, enabled: undefined, text: undefined, marker: undefined});
  if (mode === 2) Object.assign(input, {label: null, amount: 0, enabled: false, text: '', marker: OTHER_MARKER});
  if (mode === 3) Object.assign(input, {label: 'explicit', amount: 3, enabled: true, text: 'written', marker: DEFAULT_MARKER, nothing: 'value', unknown: 'known'});
  let child = createElement(DefaultedClass, input);
  if (mode === 4) return cloneElement(child, null);
  if (mode >= 5) child = cloneElement(child, {label: undefined});
  return mode === 6 ? cloneElement(child, null) : child;
}
export function ClassDefaults() {
  const [mode, setMode] = useState(0), [reset, setReset] = useState(false), [inspection, inspect] = useState('none');
  const ref = useMemo(() => createRef(), []), remembered = useRef(null);
  const child = description(mode, reset, ref);
  return <main>
    <button onClick={() => setMode(0)}>Use missing default inputs</button>
    <button onClick={() => setMode(1)}>Use undefined default inputs</button>
    <button onClick={() => setMode(2)}>Use explicit empty values</button>
    <button onClick={() => setMode(3)}>Use explicit defined values</button>
    <button onClick={() => setMode(4)}>Clone without overrides</button>
    <button onClick={() => setMode(5)}>Clone with undefined label</button>
    <button onClick={() => setMode(6)}>Clone undefined again</button>
    <button onClick={() => inspect(Object.prototype.hasOwnProperty.call(child.props, 'label') + ':' + String(child.props.label)
      + ':' + (child.props.marker === child.type.defaultProps.marker) + ':' + (Object.getPrototypeOf(child.type.defaultProps) === null))}>Inspect native default props</button>
    <button onClick={() => { remembered.current = ref.current; inspect('remembered'); }}>Remember default peer</button>
    <button onClick={() => inspect(String(remembered.current === ref.current))}>Compare default peer</button>
    <button onClick={() => setReset(true)}>Reset default key</button>
    <p data-testid="default-inspection">{inspection}</p>
    {child}
  </main>;
}

class AuthoredDefaultCounter extends Component {
  static defaultProps = {label: 'generated-default', initial: 4, children: null};
  constructor(props) { super(props); this.state = {count: props.initial}; }
  render() {
    return <section data-testid="authored-default">
      <output>{this.props.required + ':' + this.props.label + ':' + this.props.initial + ':' + this.state.count}</output>
      {this.props.children}
      <button onClick={() => this.setState(pending => ({count: pending.count + 1}))}>Increment authored default</button>
    </section>;
  }
}
export function ClassDefaultsAuthoring() {
  const [mode,setMode] = useState(0), [reset,setReset] = useState(false), [inspection,inspect] = useState('none');
  const ref = useMemo(() => createRef(), []), remembered = useRef(null);
  const key = reset ? 'replacement' : 'stable';
  const child = mode === 0 ? <AuthoredDefaultCounter required="typed" ref={ref} key={key} />
    : mode === 1 ? <AuthoredDefaultCounter initial={0} label={null} required="typed" ref={ref} key={key}><strong>authored child</strong></AuthoredDefaultCounter>
    : <AuthoredDefaultCounter {...{required: 'typed', label: 'forwarded', initial: 9, children: null}} key={key} ref={ref} />;
  return <main>
    <button onClick={() => setMode(0)}>Omit authored defaults</button>
    <button onClick={() => setMode(1)}>Override authored defaults</button>
    <button onClick={() => setMode(2)}>Forward authored props</button>
    <button onClick={() => { remembered.current = ref.current; inspect('remembered'); }}>Remember authored peer</button>
    <button onClick={() => inspect(String(remembered.current === ref.current))}>Compare authored peer</button>
    <button onClick={() => setReset(true)}>Reset authored key</button>
    <p data-testid="authored-default-inspection">{inspection}</p>
    {child}
  </main>;
}
