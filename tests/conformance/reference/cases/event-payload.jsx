import { log } from '../bench.js';

export function EventPayload() {
  return (
    <main>
      <div data-testid="area" onClick={event => log([
        event.type, event.button, event.shiftKey, event.altKey,
        event.currentTarget.tagName, event.target.tagName, event.clientX > 0, event.detail
      ].join(' '))}>
        <span data-testid="inner">inner</span>
      </div>
      <input aria-label="Keys" onKeyDown={event => log([
        event.type, event.key, event.code, event.ctrlKey, event.metaKey, event.repeat
      ].join(' '))} />
    </main>
  );
}
