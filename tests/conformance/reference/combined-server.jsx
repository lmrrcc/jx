// Framework-owned reference fixture. No request data is stored at module scope.
import {createContext, Suspense, use, useContext} from 'react';
import {renderToPipeableStream} from 'react-dom/server';

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
