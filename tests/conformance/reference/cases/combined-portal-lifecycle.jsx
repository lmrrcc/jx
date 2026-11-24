import {createContext, useContext, useLayoutEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';

const Theme = createContext('outside');

function eventLine(phase, event) {
  return [phase, event.type, event.target.id, event.target.tagName,
    event.currentTarget.id, event.currentTarget.tagName, event.defaultPrevented].join('|');
}

function PortalLifecyclePanel({input, stopped, onEvent, onLayout}) {
  const theme = useContext(Theme);
  useLayoutEffect(() => {
    const node = input.current;
    if (node === null) throw new Error('The portal ref was not attached before its layout effect');
    node.focus();
    onLayout('attached ' + node.tagName);
    return () => onLayout('cleaned');
  }, [input]);
  return <section id="portal-panel" data-testid="portal-panel" role="dialog" aria-label="Portal inspector"
    onClickCapture={event => onEvent(eventLine('panel-capture', event))}
    onClick={event => onEvent(eventLine('panel-bubble', event))}>
    <p data-testid="portal-theme">Theme: {theme}</p>
    <label htmlFor="portal-draft">Portal draft</label>
    <input id="portal-draft" ref={input} defaultValue="portal draft" />
    <a id="portal-action" href="#portal-destination" onClick={event => {
      event.preventDefault();
      if (stopped) event.stopPropagation();
      onEvent(eventLine('target', event));
    }}><span id="portal-action-label" data-testid="portal-action-label">Portal action</span></a>
  </section>;
}

export function CombinedPortalLifecycle() {
  const [opened, setOpened] = useState(false);
  const [stopped, setStopped] = useState(false);
  const [theme, setTheme] = useState('light');
  const [events, setEvents] = useState([]);
  const [layout, setLayout] = useState('not mounted');
  const [reference, setReference] = useState('uninspected');
  const input = useRef(null);
  const append = line => setEvents(previous => [...previous, line]);
  return <main>
    <button onClick={() => setOpened(true)}>Open portal</button>
    <button onClick={() => setOpened(false)}>Close portal</button>
    <button onClick={() => setTheme('dark')}>Change portal theme</button>
    <button onClick={() => setStopped(value => !value)}>Toggle portal propagation</button>
    <button onClick={() => setEvents([])}>Clear portal events</button>
    <button onClick={() => setReference(input.current === null ? 'cleared' : 'attached ' + input.current.tagName)}>Inspect portal ref</button>
    <p data-testid="portal-propagation-mode">{stopped ? 'stopped' : 'bubbling'}</p>
    <p data-testid="portal-layout">{layout}</p>
    <p data-testid="portal-ref">{reference}</p>
    <pre data-testid="portal-events">{events.join('\n')}</pre>
    <div id="portal-owner" data-testid="portal-owner"
      onClickCapture={event => append(eventLine('owner-capture', event))}
      onClick={event => append(eventLine('owner-bubble', event))}>
      <Theme.Provider value={theme}>
        {opened ? createPortal(<PortalLifecyclePanel input={input} stopped={stopped}
          onEvent={append} onLayout={setLayout} />, document.body) : null}
      </Theme.Provider>
    </div>
  </main>;
}
