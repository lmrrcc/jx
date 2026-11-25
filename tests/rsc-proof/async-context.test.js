import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Writable} from 'node:stream';
import {finished} from 'node:stream/promises';
import {cache, cacheSignal, createElement, use} from 'react';
import {renderToPipeableStream} from 'react-server-dom-webpack/server.node';
import {server as java} from './target/server.cjs';

const value = cache(() => ({}));
const read = cache((mode, data) => {
  const before = cacheSignal(), token = value('same-key');
  assert.ok(before);
  const checked = text => {
    assert.equal(cacheSignal(), before, 'request scope after continuation');
    assert.equal(value('same-key'), token, 'request cache after continuation');
    return text;
  };
  if (mode === 'MAP') return data.then(checked);
  if (mode === 'RECOVER') return data.catch(error => checked(error.message));
  return (async () => checked(await data))();
});
function ReferencePage({mode, data, ready}) {
  const pending = read(mode, data);
  ready();
  return createElement('p', {'data-testid': 'async-context'}, use(pending));
}
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return {promise, resolve, reject};
}
function render(side, mode, data, shared = null) {
  const ready = deferred(), chunks = [], errors = [];
  let bytes = 0;
  const sink = new Writable({write(chunk, encoding, complete) {
    bytes += chunk.length;
    if (bytes > 131072) return complete(new Error('Async context output exceeded limit'));
    chunks.push(chunk); complete();
  }});
  const element = side === 'jx' ? (shared ? shared.render(mode, ready.resolve) : java.asyncContext(mode, data, ready.resolve))
    : createElement(ReferencePage, {mode, data, ready: ready.resolve});
  const stream = renderToPipeableStream(element, {}, {onError(error) { errors.push(error); return 'ASYNC_CONTEXT_FAILED'; }});
  const done = finished(sink, {cleanup: true});
  done.catch(() => {});
  stream.pipe(sink);
  return {done, errors,
    ready: Promise.race([ready.promise, done.then(() => { throw new Error('Render ended before suspension'); })]),
    payload: () => Buffer.concat(chunks).toString('utf8'),
    dispose() { stream.abort(); if (!sink.destroyed) sink.destroy(); }
  };
}
for (const side of ['reference', 'jx']) for (const mode of ['AWAIT', 'MAP', 'RECOVER']) {
  test(side + ': ' + mode + ' preserves independent request caches after external settlement', async () => {
    const a = deferred(), b = deferred();
    const alpha = render(side, mode, a.promise), bravo = render(side, mode, b.promise);
    try {
      await Promise.all([alpha.ready, bravo.ready]);
      if (mode === 'RECOVER') b.reject(new Error('bravodata')); else b.resolve('bravodata');
      await bravo.done;
      assert.deepEqual(bravo.errors.map(error => error.message), []);
      assert.ok(bravo.payload().includes('bravodata'));
      assert.ok(!bravo.payload().includes('alphadata'));
      if (mode === 'RECOVER') a.reject(new Error('alphadata')); else a.resolve('alphadata');
      await alpha.done;
      assert.deepEqual(alpha.errors.map(error => error.message), []);
      assert.ok(alpha.payload().includes('alphadata'));
      assert.ok(!alpha.payload().includes('bravodata'));
    } finally { alpha.dispose(); bravo.dispose(); }
  });
}

for (const side of ['reference', 'jx']) for (const mode of ['AWAIT', 'MAP', 'RECOVER']) {
  test(side + ': ' + mode + ' keeps two observer scopes on one shared promise', async () => {
    const data = deferred();
    const shared = side === 'jx' ? java.asyncShared(data.promise) : null;
    const alpha = render(side, mode, data.promise, shared), bravo = render(side, mode, data.promise, shared);
    try {
      await Promise.all([alpha.ready, bravo.ready]);
      if (mode === 'RECOVER') data.reject(new Error('shareddata')); else data.resolve('shareddata');
      await Promise.all([alpha.done, bravo.done]);
      for (const request of [alpha, bravo]) {
        assert.deepEqual(request.errors.map(error => error.message), []);
        assert.ok(request.payload().includes('shareddata'));
      }
      // Register under a new request after the shared promise has already settled.
      const late = render(side, mode, data.promise, shared);
      try {
        await late.done;
        assert.deepEqual(late.errors.map(error => error.message), []);
        assert.ok(late.payload().includes('shareddata'));
      } finally { late.dispose(); }
    } finally { alpha.dispose(); bravo.dispose(); }
  });
}
