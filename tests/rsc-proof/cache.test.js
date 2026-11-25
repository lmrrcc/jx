import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Writable} from 'node:stream';
import {finished} from 'node:stream/promises';
import {setImmediate} from 'node:timers/promises';
import {renderToPipeableStream} from 'react-server-dom-webpack/server.node';
import {server as java} from './target/server.cjs';
import {reference} from './cache-reference.js';

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return {promise, resolve, reject};
}
function render(host, data) {
  const ready = deferred();
  const handle = host.cache(data, ready.resolve);
  const chunks = [], errors = [];
  let bytes = 0;
  const sink = new Writable({write(chunk, encoding, complete) {
    bytes += chunk.length;
    if (bytes > 262144) return complete(new Error('Cache proof exceeded limit'));
    chunks.push(chunk);
    complete();
  }});
  const stream = renderToPipeableStream(handle.element, {}, {onError(error) { errors.push(error); return 'CACHE_PROOF_FAILED'; }});
  const done = finished(sink, {cleanup: true});
  done.catch(() => {});
  stream.pipe(sink);
  return {done, errors, snapshot: handle.snapshot, late: handle.late,
    ready: Promise.race([ready.promise, done.then(() => { throw new Error('Render ended before cache checks: ' + errors.map(error => error.message)); })]),
    payload: () => Buffer.concat(chunks).toString('utf8'),
    abort: () => stream.abort(new Error('Cache proof aborted')),
    dispose() { handle.close(); stream.abort(); if (!sink.destroyed) sink.destroy(); }
  };
}

for (const [name, host] of [['reference', reference], ['jx', java]]) {
  test(name + ': cache factory is lazy and outside-render calls are uncached', () => {
    assert.equal(host.outside(), true);
  });

  test(name + ': cache values, failures and promises remain scoped across concurrent suspended requests', async () => {
    const a = deferred(), b = deferred();
    const alpha = render(host, a.promise), bravo = render(host, b.promise);
    try {
      await Promise.all([alpha.ready, bravo.ready]);
      const first = alpha.snapshot(), second = bravo.snapshot();
      assert.equal(first.checked, true); assert.equal(second.checked, true);
      assert.notEqual(first.token, second.token, 'the same key must have a separate request cache');
      assert.notEqual(first.async, second.async, 'a cached Java promise must not cross requests');
      assert.equal(first.aborted, false); assert.equal(second.aborted, false);
      b.resolve('bravodata');
      await bravo.done;
      await setImmediate();
      assert.equal(bravo.snapshot().token, second.token);
      assert.equal(bravo.snapshot().async, second.async);
      assert.equal(bravo.snapshot().data, 'bravodata');
      assert.equal(bravo.snapshot().aborted, true);
      assert.equal(bravo.snapshot().aborts, 1);
      assert.equal(bravo.snapshot().removed, 0);
      assert.equal(alpha.snapshot().aborted, false, 'one completed scope must not cancel another');
      bravo.late();
      assert.equal(bravo.snapshot().late, 1, 'an already-aborted signal must notify synchronously');
      a.resolve('alphadata');
      await alpha.done;
      await setImmediate();
      assert.equal(alpha.snapshot().token, first.token);
      assert.equal(alpha.snapshot().data, 'alphadata');
      assert.equal(alpha.snapshot().aborts, 1);
      assert.equal(alpha.snapshot().removed, 0);
      assert.deepEqual([...alpha.errors, ...bravo.errors], []);
      assert.equal(host.outside(), true);
    } finally { alpha.dispose(); bravo.dispose(); }
  });

  test(name + ': abort ends the cache signal and late work cannot publish', async () => {
    const data = deferred();
    const request = render(host, data.promise);
    try {
      await request.ready;
      request.abort();
      await request.done;
      await setImmediate();
      assert.equal(request.snapshot().aborted, true);
      assert.equal(request.snapshot().aborts, 1);
      assert.equal(request.snapshot().removed, 0);
      assert.equal(request.errors.length, 1);
      const payload = request.payload();
      data.resolve('toolate');
      await setImmediate();
      assert.equal(request.payload(), payload);
      assert.equal(request.snapshot().data, 'pending');
    } finally { request.dispose(); }
  });

  test(name + ': render failure also ends the cache signal', async () => {
    const data = deferred();
    const request = render(host, data.promise);
    try {
      await request.ready;
      data.reject(new Error('Cache input failed'));
      await request.done;
      await setImmediate();
      assert.equal(request.errors.length, 1);
      assert.ok(request.payload().includes('CACHE_PROOF_FAILED'));
      assert.equal(request.snapshot().aborted, true);
      assert.equal(request.snapshot().aborts, 1);
      assert.equal(request.snapshot().removed, 0);
    } finally { request.dispose(); }
  });
}
