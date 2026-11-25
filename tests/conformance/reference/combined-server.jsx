// Framework-owned reference fixture. No request data is stored at module scope.
import {createContext, Suspense, use, useContext} from 'react';
import {renderToPipeableStream} from 'react-dom/server';
import {Writable} from 'node:stream';
import {finished} from 'node:stream/promises';

const RequestContext = createContext('outside-request');

function BrokenShell() { throw new Error('fatal shell failed'); }

function Result({data}) {
  const result = use(data);
  const request = useContext(RequestContext);
  return <p data-testid="arrived" data-request={request}>{request + ':' + result}</p>;
}

function Streaming({request, data}) {
  return <RequestContext value={request}>
    <main>
      <h1>{'shell-' + request}</h1>
      <Suspense fallback={<p data-testid="fallback">{'waiting-' + request}</p>}>
        <Result data={data} />
      </Suspense>
    </main>
  </RequestContext>;
}

export const combinedServer = {
  startSinkCompletion(mode) {
    const data = Promise.withResolvers();
    const shell = Promise.withResolvers();
    const failure = new Error('sink failed');
    let html = '', writes = 0, ends = 0, originalFailure = false;
    const stream = renderToPipeableStream(<Streaming request="sink" data={data.promise} />, {
      onShellReady: shell.resolve, onShellError: shell.reject, onError() {}
    });
    return {
      shellReady: shell.promise,
      pipe() {
        const decoder = new TextDecoder();
        const sink = new Writable({
          write(bytes, encoding, done) {
            writes++;
            const chunk = decoder.decode(bytes, {stream: true});
            if (mode === 'first' || mode === 'late' && chunk.includes('data-testid="arrived"')) return done(failure);
            html += chunk;
            done();
          },
          final(done) {
            ends++;
            if (mode === 'end') return done(failure);
            html += decoder.decode();
            done();
          }
        });
        const delivered = finished(sink, {cleanup: true});
        delivered.catch(error => { originalFailure = error === failure; });
        try { stream.pipe(sink); }
        catch (error) { sink.destroy(); return Promise.reject(error); }
        return delivered;
      },
      release: () => data.resolve('arrived-è-😀' + 'è😀'.repeat(900)), abort: () => stream.abort(),
      html: () => html, writes: () => writes, ends: () => ends, originalFailure: () => originalFailure
    };
  },
  startFatalStream() {
    const shell = Promise.withResolvers();
    const all = Promise.withResolvers();
    let errors = 0;
    const stream = renderToPipeableStream(<BrokenShell />, {
      onShellReady: shell.resolve,
      // Native React signals a fatal root failure here. Neither readiness promise
      // can succeed when that root has no output; both consumers must be notified.
      onShellError(error) { shell.reject(error); all.reject(error); },
      onAllReady: all.resolve,
      onError() { errors++; }
    });
    return {shellReady: shell.promise, allReady: all.promise,
      abort: () => stream.abort(), errors: () => errors};
  },
  startCombinedStream(request) {
    const data = Promise.withResolvers();
    const shell = Promise.withResolvers();
    const all = Promise.withResolvers();
    let errors = 0;
    const stream = renderToPipeableStream(<Streaming request={request} data={data.promise} />, {
      identifierPrefix: request + '-',
      onShellReady: shell.resolve,
      onShellError: shell.reject,
      onAllReady: all.resolve,
      onError() { errors++; }
    });
    return {
      shellReady: shell.promise,
      allReady: all.promise,
      pipe: writable => stream.pipe(writable),
      release: data.resolve,
      abort: () => stream.abort(),
      errors: () => errors
    };
  }
};
