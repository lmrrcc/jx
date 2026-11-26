import * as React from 'react';
import {createRoot} from 'react-dom/client';

// Native oracle for development effect replay on Activity reconnection.
export function resourceLifecycleProbe() {
  const events = [];
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);
  function NativeEffect({value}) {
    React.useEffect(() => {
      events.push({kind: 'start', value});
      return () => events.push({kind: 'cancel', value});
    }, [value]);
    return React.createElement('span', null, value);
  }
  return {
    events,
    render(value, visible) {
      root.render(React.createElement(React.StrictMode, null,
        React.createElement(React.Activity, {mode: visible ? 'visible' : 'hidden'},
          React.createElement(NativeEffect, {value}))));
    },
    close() {root.unmount(); container.remove();}
  };
}
