import { log } from '../bench.js';

export function CapturePhase() {
  return (
    <main>
      <div onClickCapture={() => log('capture outer')} onClick={() => log('bubble outer')}>
        <button onClickCapture={() => log('capture target')} onClick={() => log('bubble target')}>Press</button>
      </div>
    </main>
  );
}
