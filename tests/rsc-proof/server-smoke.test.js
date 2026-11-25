import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Writable} from 'node:stream';
import {finished} from 'node:stream/promises';
import {setImmediate} from 'node:timers/promises';
import {renderToPipeableStream, registerClientReference} from 'react-server-dom-webpack/server.node';
import {server} from './target/server.cjs';

test('Java server component renders through the real Flight server runtime', async () => {
  const errors = [];
  const output = [];
  let bytes = 0;
  const sink = new Writable({write(chunk, encoding, complete) {
    bytes += chunk.length;
    if (bytes > 65536) return complete(new Error('Proof output exceeded limit'));
    output.push(chunk);
    complete();
  }});
  const stream = renderToPipeableStream(server.render('alpha', null, null), {}, {onError(error) { errors.push(error); }});
  const done = finished(sink, {cleanup: true});
  try {
    stream.pipe(sink);
    await done;
    assert.deepEqual(errors, []);
    const payload = Buffer.concat(output).toString('utf8');
    assert.ok(payload.includes('Java Server Component'));
    assert.ok(payload.includes('alpha'));
    assert.ok(!payload.includes('<main>'), 'Flight must not be substituted with HTML');
  } finally {
    stream.abort();
    if (!sink.destroyed) sink.destroy();
  }
});

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return {promise, resolve, reject};
}

// The sink records real Flight bytes. The first chunk is a controlled scheduling
// boundary; no sleep decides whether the request is still suspended.
function render(label, promise, counter = null) {
  const first = deferred();
  const chunks = [];
  const errors = [];
  let bytes = 0;
  let closed = false;
  const sink = new Writable({write(chunk, encoding, complete) {
    bytes += chunk.length;
    if (bytes > 65536) return complete(new Error('Proof output exceeded limit'));
    chunks.push(chunk);
    first.resolve();
    complete();
  }});
  const stream = renderToPipeableStream(server.render(label, counter, promise), {}, {
    onError(error) { errors.push(error); return 'RSC_PROOF_FAILED'; }
  });
  const done = finished(sink, {cleanup: true}).finally(() => { closed = true; });
  // Observe failures immediately even when the test is currently awaiting first.
  done.catch(error => first.reject(error));
  stream.pipe(sink);
  return {first: first.promise, done, errors, get closed() { return closed; },
    payload: () => Buffer.concat(chunks).toString('utf8'),
    abort: () => stream.abort(new Error('Proof request aborted')),
    dispose() { stream.abort(); if (!sink.destroyed) sink.destroy(); }
  };
}

test('Java use suspends two Flight requests and resumes each with its own data', async () => {
  const a = deferred(), b = deferred();
  const alpha = render('alpha', a.promise), bravo = render('bravo', b.promise);
  try {
    await Promise.all([alpha.first, bravo.first]);
    assert.equal(alpha.closed, false);
    assert.equal(bravo.closed, false);
    b.resolve('bravodata');
    await bravo.done;
    assert.ok(bravo.payload().includes('bravodata'));
    assert.ok(!bravo.payload().includes('alphadata'));
    assert.equal(alpha.closed, false);
    a.resolve('alphadata');
    await alpha.done;
    assert.ok(alpha.payload().includes('alphadata'));
    assert.ok(!alpha.payload().includes('bravodata'));
    assert.deepEqual([...alpha.errors, ...bravo.errors], []);
  } finally { alpha.dispose(); bravo.dispose(); }
});

test('rejected Java data produces the Flight error record and terminates the request', async () => {
  const data = deferred();
  const request = render('failed', data.promise);
  try {
    await request.first;
    data.reject(new Error('Fixture data rejected'));
    await request.done;
    assert.equal(request.errors.length, 1);
    assert.ok(request.payload().includes('RSC_PROOF_FAILED'));
    assert.ok(!request.payload().includes('"data-testid":"server-data"'));
  } finally { request.dispose(); }
});

test('aborting suspended Flight finishes it and late data cannot append bytes', async () => {
  const data = deferred();
  const request = render('aborted', data.promise);
  try {
    await request.first;
    request.abort();
    await request.done;
    assert.equal(request.errors.length, 1);
    const before = request.payload();
    assert.ok(before.includes('RSC_PROOF_FAILED'));
    data.resolve('latepayload');
    await setImmediate(); // drain promise reactions and React's queued retry
    assert.equal(request.payload(), before);
    assert.ok(!request.payload().includes('latepayload'));
  } finally { request.dispose(); }
});

test('a client reference absent from the manifest fails explicitly', async () => {
  const unknown = registerClientReference(() => {}, 'file:///not-in-build.js', 'Counter');
  const request = render('unknown', null, unknown);
  try {
    await request.done;
    // Development Flight can report the same missing reference from both the
    // component debug model and the rendered element. Each must be a manifest error.
    assert.ok(request.errors.length > 0);
    for (const error of request.errors) assert.match(error.message, /React Client Manifest/);
    assert.ok(request.payload().includes('RSC_PROOF_FAILED'));
  } finally { request.dispose(); }
});
