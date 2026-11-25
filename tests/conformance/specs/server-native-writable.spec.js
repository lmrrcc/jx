import {createServer, get} from 'node:http';
import {Writable} from 'node:stream';
import {finished} from 'node:stream/promises';
import {expect} from '@playwright/test';
import {server as onServer} from './bench.js';

const payload = 'arrived-è-😀' + 'è😀'.repeat(900);

onServer('server-native-backpressure: a borrowed Writable resumes only after drain and preserves all UTF-8 bytes', async server => {
  const request = server.startNativePressure();
  const blocked = Promise.withResolvers();
  let releaseFirst;
  let calls = 0, drains = 0;
  const queued = [];
  const chunks = [];
  const sink = new Writable({
    highWaterMark: 1,
    write(chunk, encoding, done) {
      chunks.push(Buffer.from(chunk));
      if (releaseFirst === undefined) {
        releaseFirst = done;
        blocked.resolve();
      } else queueMicrotask(done);
    }
  });
  const nativeWrite = sink.write;
  sink.write = function (...args) { calls++; queued.push(Buffer.from(args[0])); return nativeWrite.apply(this, args); };
  sink.on('drain', () => drains++);
  const delivered = finished(sink, {cleanup: true});
  // Attach a rejection observer before starting native work; the assertion still
  // awaits the original delivery promise rather than converting a failure to success.
  delivered.catch(() => {});
  try {
    await request.shellReady;
    request.pipe(sink);
    await blocked.promise;
    expect(calls).toBe(1);
    expect(sink.writableNeedDrain).toBe(true);
    expect(Buffer.concat(chunks).toString('utf8')).toContain('waiting-pressure');
    request.release(payload);
    await request.allReady;
    // React may finish buffering a whole boundary despite write(false), but must
    // leave later completed boundaries queued until drain. It does not promise a
    // hard one-write/one-byte cap. The original one-boundary probe failed on both
    // native React and JX, so it could not establish backpressure.
    expect(calls).toBeGreaterThan(1);
    expect(chunks).toHaveLength(1);
    const paused = Buffer.concat(queued).toString('utf8');
    expect(paused).toContain('data-testid="segment-0"');
    expect(paused).not.toContain('data-testid="segment-1"');
    expect(paused).not.toContain('data-testid="segment-4"');
    expect(sink.writableFinished).toBe(false);
    const acknowledge = releaseFirst;
    releaseFirst = null;
    acknowledge();
    await delivered;
    expect(calls).toBeGreaterThan(1);
    expect(sink.writableFinished).toBe(true);
    expect(sink.closed).toBe(true);
    const html = Buffer.concat(chunks).toString('utf8');
    for (let index = 0; index < 5; index++) expect(html).toContain('data-testid="segment-' + index + '"');
    expect(html.split('pressure:' + payload)).toHaveLength(6);
    expect(drains).toBeGreaterThan(0);
    expect(request.errors()).toBe(0);
  } finally {
    if (releaseFirst) releaseFirst();
    request.abort();
    request.release('disposed');
    if (!sink.destroyed) sink.destroy();
    await delivered.catch(() => {});
  }
});

onServer('server-native-failure: asynchronous write and final errors retain native identity and isolate sibling delivery', async server => {
  for (const mode of ['first', 'late', 'final']) {
    const request = server.startCombinedStream('failed-' + mode);
    const sibling = server.startCombinedStream('survivor-' + mode);
    const failure = new Error('native destination failed: ' + mode);
    const shell = Promise.withResolvers();
    const chunks = [], siblingChunks = [];
    let writes = 0, finals = 0;
    const sink = new Writable({
      write(chunk, encoding, done) {
        writes++;
        chunks.push(Buffer.from(chunk));
        if (Buffer.concat(chunks).toString('utf8').includes('data-testid="fallback"')) shell.resolve();
        queueMicrotask(() => done(mode === 'first' || mode === 'late' && chunk.toString().includes('data-testid="arrived"') ? failure : undefined));
      },
      final(done) { finals++; queueMicrotask(() => done(mode === 'final' ? failure : undefined)); }
    });
    const survivor = new Writable({write(chunk, encoding, done) { siblingChunks.push(Buffer.from(chunk)); queueMicrotask(done); }});
    const delivered = finished(sink, {cleanup: true});
    const siblingDone = finished(survivor, {cleanup: true});
    delivered.catch(() => {}); siblingDone.catch(() => {});
    try {
      await Promise.all([request.shellReady, sibling.shellReady]);
      request.pipe(sink); sibling.pipe(survivor);
      if (mode !== 'first') { await shell.promise; request.release(payload); }
      await expect(delivered).rejects.toBe(failure);
      expect(sink.errored).toBe(failure);
      expect(sink.destroyed).toBe(true);
      expect(sink.writableFinished).toBe(false);
      expect(finals).toBe(mode === 'final' ? 1 : 0);
      const stopped = [writes, finals, Buffer.concat(chunks).toString('utf8')];
      request.release('late-discarded');
      sibling.release(payload);
      await Promise.all([request.allReady, sibling.allReady, siblingDone]);
      expect([writes, finals, Buffer.concat(chunks).toString('utf8')]).toEqual(stopped);
      expect(Buffer.concat(siblingChunks).toString('utf8')).toContain('survivor-' + mode + ':' + payload);
      expect(Buffer.concat(siblingChunks).toString('utf8')).not.toContain('failed-');
      expect(survivor.writableFinished).toBe(true);
      expect(sibling.errors()).toBe(0);
    } finally {
      request.abort(); sibling.abort();
      request.release('disposed'); sibling.release('disposed');
      if (!sink.destroyed) sink.destroy();
      if (!survivor.destroyed) survivor.destroy();
      await Promise.allSettled([delivered, siblingDone]);
    }
  }
});

onServer('server-http-disconnect: closing a real HTTP client aborts its render while another response completes', async server => {
  const requests = new Map();
  const hostErrors = [];
  const host = createServer((incoming, response) => {
    const id = incoming.url === '/dropped' ? 'dropped' : incoming.url === '/survivor' ? 'survivor' : null;
    if (id === null || requests.has(id)) { response.writeHead(404); response.end(); return; }
    const session = server.startCombinedStream(id);
    const closed = Promise.withResolvers();
    const delivery = finished(response, {cleanup: true});
    delivery.catch(() => {});
    const state = {session, response, delivery, closed: closed.promise, disconnects: 0};
    requests.set(id, state);
    // This is the host's explicit policy, not an implicit guarantee of a borrowed
    // destination: disconnect owns cancellation of this request's renderer.
    response.once('close', () => {
      if (!response.writableFinished) { state.disconnects++; session.abort(); }
      closed.resolve();
    });
    session.shellReady.then(() => {
      response.setHeader('Content-Type', 'text/html; charset=utf-8');
      session.pipe(response);
    }).catch(error => { hostErrors.push(error); response.destroy(error); });
  });
  const clients = [];
  function connect(path, port) {
    const shell = Promise.withResolvers(), body = Promise.withResolvers();
    const chunks = [];
    const client = {shell: shell.promise, body: body.promise, response: null, request: null};
    // Both expected disconnect failures and unexpected transport failures remain
    // observable to the caller, with early handlers to avoid unhandled rejections.
    shell.promise.catch(() => {}); body.promise.catch(() => {});
    client.request = get({host: '127.0.0.1', port, path, agent: false}, response => {
      client.response = response;
      response.on('data', chunk => {
        chunks.push(Buffer.from(chunk));
        if (Buffer.concat(chunks).toString('utf8').includes('data-testid="fallback"')) shell.resolve();
      });
      response.once('end', () => body.resolve(Buffer.concat(chunks).toString('utf8')));
      response.once('error', error => { shell.reject(error); body.reject(error); });
      response.once('close', () => {
        if (!response.complete) { const error = new Error('incomplete HTTP response'); shell.reject(error); body.reject(error); }
      });
    });
    client.request.once('error', error => { shell.reject(error); body.reject(error); });
    clients.push(client);
    return client;
  }
  try {
    await new Promise((resolve, reject) => { host.once('error', reject); host.listen(0, '127.0.0.1', resolve); });
    const dropped = connect('/dropped', host.address().port);
    const survivor = connect('/survivor', host.address().port);
    await Promise.all([dropped.shell, survivor.shell]);
    const failed = requests.get('dropped'), live = requests.get('survivor');
    expect(dropped.response.statusCode).toBe(200);
    expect(survivor.response.statusCode).toBe(200);
    expect(live.response.writableFinished).toBe(false);
    dropped.response.destroy();
    await failed.closed;
    await expect(failed.delivery).rejects.toMatchObject({code: 'ERR_STREAM_PREMATURE_CLOSE'});
    await expect(dropped.body).rejects.toBeInstanceOf(Error);
    await failed.session.allReady;
    expect(failed.disconnects).toBe(1);
    expect(failed.response.writableFinished).toBe(false);
    failed.session.release('late-discarded');
    live.session.release(payload);
    const html = await survivor.body;
    await Promise.all([live.delivery, live.closed, live.session.allReady]);
    expect(html).toContain('survivor:' + payload);
    expect(html).not.toContain('dropped');
    expect(live.disconnects).toBe(0);
    expect(live.response.writableFinished).toBe(true);
    expect(live.session.errors()).toBe(0);
    expect(hostErrors).toEqual([]);
  } finally {
    for (const state of requests.values()) { state.session.abort(); state.session.release('disposed'); }
    for (const client of clients) { client.response?.destroy(); client.request.destroy(); }
    host.closeAllConnections();
    if (host.listening) await new Promise((resolve, reject) => host.close(error => error ? reject(error) : resolve()));
    await Promise.allSettled([...requests.values()].map(state => state.delivery));
  }
});
