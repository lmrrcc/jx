import {Writable} from 'node:stream';
import {finished} from 'node:stream/promises';
import {expect} from '@playwright/test';
import {server as onServer, interaction} from './bench.js';

// Observe real bytes and Writable lifecycle. No completion flag is supplied by the fixture.
function observe(session) {
  const shell = Promise.withResolvers();
  let html = '';
  let writes = 0;
  const sink = new Writable({
    write(chunk, encoding, callback) {
      html += chunk.toString();
      writes++;
      if (html.includes('data-testid="fallback"')) shell.resolve(html);
      callback();
    }
  });
  const done = finished(sink, {cleanup: true});
  session.pipe(sink);
  return {shell: shell.promise, done, sink, html: () => html, writes: () => writes};
}

onServer('combined-server-stream: delivers gated shells and isolates an aborted request from a resumed one', async server => {
  const bravo = server.startCombinedStream('bravo');
  // Complete the older request after the newer provider has rendered and aborted;
  // a broken global "current request" cannot accidentally match the survivor.
  const alpha = server.startCombinedStream('alpha');
  const observed = [];
  try {
    await Promise.all([alpha.shellReady, bravo.shellReady]);
    const a = observe(alpha);
    const b = observe(bravo);
    observed.push(a, b);

    await interaction('stream-use-gate', async () => {
      // Neither data promise has been released. Awaiting these bytes would deadlock
      // if the implementation buffered everything until allReady.
      const [firstA, firstB] = await Promise.all([a.shell, b.shell]);
      expect(firstA).toContain('<h1>shell-alpha</h1>');
      expect(firstA).toContain('waiting-alpha');
      expect(firstA).not.toContain('data-testid="arrived"');
      expect(firstB).toContain('<h1>shell-bravo</h1>');
      expect(firstB).toContain('waiting-bravo');
      expect(firstB).not.toContain('data-testid="arrived"');
      expect(a.sink.writableFinished).toBe(false);
      expect(b.sink.writableFinished).toBe(false);
    });

    await interaction('stream-context-isolation', async () => {
      alpha.abort();
      await Promise.all([alpha.allReady, a.done]);
      expect(a.sink.writableFinished).toBe(true);
      expect(a.sink.closed).toBe(true);
      expect(alpha.errors()).toBe(1);
      expect(a.html()).toContain('waiting-alpha');
      expect(a.html()).not.toContain('data-testid="arrived"');
      expect(b.sink.writableFinished).toBe(false);
      expect(bravo.errors()).toBe(0);

      const stopped = {html: a.html(), writes: a.writes()};
      // Renderer abort does not cancel arbitrary application promises. Late data
      // may still settle, but must never write into the closed response or B.
      alpha.release('discarded-alpha');
      bravo.release('value-bravo');
      await Promise.all([bravo.allReady, b.done]);
      expect(b.sink.writableFinished).toBe(true);
      expect(b.sink.closed).toBe(true);
      expect(b.html()).toContain('<p data-testid="arrived" data-request="bravo">bravo:value-bravo</p>');
      expect(b.html()).not.toContain('alpha');
      expect(b.html().indexOf('waiting-bravo')).toBeLessThan(b.html().indexOf('bravo:value-bravo'));
      expect(bravo.errors()).toBe(0);
      expect({html: a.html(), writes: a.writes()}).toEqual(stopped);
    });
  } finally {
    alpha.abort();
    bravo.abort();
    for (const output of observed) if (!output.sink.destroyed) output.sink.destroy();
  }
});
