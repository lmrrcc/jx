import { log } from '../bench.js';

export function TransferEvents() {
  return (
    <main>
      <input aria-label="Compose"
             onCompositionStart={event => log('compositionstart [' + event.data + ']')}
             onCompositionUpdate={event => log('compositionupdate [' + event.data + ']')}
             onCompositionEnd={event => log('compositionend [' + event.data + ']')}
             onPaste={event => {
               event.preventDefault();
               log('paste ' + event.clipboardData.getData('text/plain'));
             }}
             onCopy={event => {
               event.preventDefault();
               event.clipboardData.setData('text/plain', 'copied by handler');
               log('copy');
             }} />
      <div data-testid="source" draggable onDragStart={event => {
        event.dataTransfer.setData('text/plain', 'dragged item');
        log('dragstart');
      }}>source</div>
      <div data-testid="target" onDragOver={event => event.preventDefault()} onDrop={event => {
        event.preventDefault();
        log('drop ' + event.dataTransfer.getData('text/plain'));
      }}>target</div>
    </main>
  );
}
