import {Children, StrictMode, isValidElement, useState} from 'react';
import {createPortal} from 'react-dom';

function onlyOutcome(children) {
  try {
    Children.only(children);
    return 'accepted';
  } catch {
    return 'rejected';
  }
}

function inspect(name, children) {
  const visited = [];
  Children.forEach(children, child => visited.push(child));
  return <section aria-label={name}>
    <p data-testid={name + '-count'}>{Children.count(children)}</p>
    <p data-testid={name + '-listed'}>{Children.toArray(children).length}</p>
    <p data-testid={name + '-visited'}>{visited.length}</p>
    <p data-testid={name + '-only'}>{onlyOutcome(children)}</p>
    <ul data-testid={name + '-mapped'}>{Children.map(children, child => <li>{child}</li>)}</ul>
  </section>;
}

function Probe({name, children}) {
  return inspect(name, children);
}

function composedKeys(values) {
  return Children.toArray(values.map(value => <span key={value} />)).map(element => element.key).join('|');
}

export function CombinedChildrenContract() {
  const [nativeNodes, setNativeNodes] = useState('uninspected');
  const fragment = <><b>left</b><i>right</i></>;
  const nested = [<b key="a">A</b>, [null, false, ['text', <i key="b">B</i>]]];
  return <main>
    {inspect('direct-fragment', fragment)}
    {inspect('empty-fragment', <></>)}
    <Probe name="prop-fragment">{fragment}</Probe>
    <Probe name="attribute-fragment" children={fragment} />
    <Probe name="element"><em>one</em></Probe>
    <Probe name="nested">{nested}</Probe>
    <Probe name="singleton-array">{[<em key="one">array</em>]}</Probe>
    <Probe name="text">text</Probe>
    <Probe name="null">{null}</Probe>
    <p data-testid="numeric-keys">{composedKeys([
      1.0, -0.0, 1e-6, 1e-7, 1e20, 1e21, 1e23, Number.MIN_VALUE, Number.MAX_VALUE,
      Math.fround(1.1), NaN, Infinity, -Infinity, Number.MAX_SAFE_INTEGER
    ])}</p>
    <p data-testid="escaped-keys">{composedKeys(['a:b=c', '=:', 'slash/key'])}</p>
    <p data-testid="native-nodes">{nativeNodes}</p>
    <button onClick={() => {
      const strict = <StrictMode><b data-testid="native-strict-content">strict</b></StrictMode>;
      const portal = createPortal(<b data-testid="native-portal-content">portal</b>, document.body);
      setNativeNodes('strict ' + isValidElement(strict) + ' ' + onlyOutcome(strict) + ' ' + Children.count(strict)
        + '; portal ' + isValidElement(portal) + ' ' + onlyOutcome(portal) + ' ' + Children.count(portal));
    }}>Inspect native nodes</button>
  </main>;
}
