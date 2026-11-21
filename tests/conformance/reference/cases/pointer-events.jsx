import { log } from '../bench.js';

export function PointerEvents() {
  return (
    <main>
      <input id="first" aria-label="First"
             onFocus={event => log('focus ' + event.target.tagName + ' ' + (event.relatedTarget ? event.relatedTarget.id : 'none'))}
             onBlur={event => log('blur ' + (event.relatedTarget ? event.relatedTarget.id : 'none'))} />
      <input id="second" aria-label="Second" />
      <div data-testid="pad"
           onMouseEnter={event => log('mouseenter ' + event.currentTarget.tagName)}
           onPointerDown={event => log('pointerdown ' + event.pointerType + ' ' + event.isPrimary + ' ' + event.button)}
           onWheel={event => log('wheel ' + Math.sign(event.deltaY) + ' ' + event.deltaMode)}
           onTouchStart={event => log('touchstart ' + event.touches.length + ' ' + event.changedTouches[0].identifier)}>pad</div>
    </main>
  );
}
