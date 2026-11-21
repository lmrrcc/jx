import { log } from '../bench.js';

export function MediaEvents() {
  return (
    <main>
      <div data-testid="box"
           onAnimationEnd={event => log('animationend ' + event.animationName + ' ' + event.elapsedTime)}
           onTransitionEnd={event => log('transitionend ' + event.propertyName)}>box</div>
      <audio data-testid="audio"
             onPlay={event => log('play ' + event.target.tagName + ' ' + event.target.paused)}
             onPause={event => log('pause ' + event.currentTarget.tagName)}
             onTimeUpdate={event => log('timeupdate ' + Math.trunc(event.target.currentTime))} />
      <img alt="Picture" src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7" onLoad={event => log('load ' + event.target.tagName)} />
    </main>
  );
}
