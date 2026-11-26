// Framework-owned HTTP/renderer test host. Spring alone authenticates sessions
// and selects private domain data. The UI, DTO codecs and rendering are Java.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {finished} from 'node:stream/promises';
import {timingSafeEqual} from 'node:crypto';

const {renderer} = createRequire(import.meta.url)('./target/server.cjs');
const port = Number(process.env.PROTECTED_SSR_PORT || 4884);
const backend = new URL(process.env.PROTECTED_BACKEND_ORIGIN || 'http://127.0.0.1:4885');
const proofToken = process.env.PROTECTED_PROOF_TOKEN;
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid SSR port');
if (backend.protocol !== 'http:' || backend.hostname !== '127.0.0.1' || backend.username || backend.password
    || backend.pathname !== '/' || backend.search || backend.hash) throw new Error('Proof backend must be a loopback HTTP origin');
if (!proofToken || !/^[a-f0-9]{48}$/.test(proofToken)) throw new Error('Missing generated proof control token');

const MAX_BODY = 1_048_576;
const active = new Map(), history = new Map();
const application = await readFile(new URL('./target/site/application.js', import.meta.url));
if (application.length > 8 * MAX_BODY) throw new Error('Browser proof bundle exceeds limit');

function controlAuthorized(request) {
  const token = request.headers['x-jx-proof'];
  return typeof token === 'string' && /^[a-f0-9]{48}$/.test(token)
    && timingSafeEqual(Buffer.from(token), Buffer.from(proofToken));
}

function cookie(request) {
  const source = request.headers.cookie;
  if (typeof source !== 'string' || source.length > 4096) return null;
  const candidates = source.split(';').map(part => part.trim()).filter(part => part.startsWith(renderer.cookie + '='));
  if (candidates.length !== 1) return null;
  const value = candidates[0].slice(renderer.cookie.length + 1);
  if (!/^[\x21\x23-\x2b\x2d-\x3a\x3c-\x5b\x5d-\x7e]{1,256}$/.test(value)) return null;
  return renderer.cookie + '=' + value;
}

class BackendFailure extends Error {
  constructor(status) { super('Protected backend request failed'); this.status = status; }
}

async function data(path, sessionCookie, signal) {
  // The paths come from the compiled shared Java contract, not client input.
  const url = new URL(path, backend);
  if (url.origin !== backend.origin) throw new Error('Backend endpoint escaped its configured origin');
  const response = await fetch(url, {headers: {Accept: 'application/json', Cookie: sessionCookie}, redirect: 'error', signal});
  if (response.status !== 200 || !response.headers.get('content-type')?.startsWith('application/json')) {
    await response.body?.cancel();
    throw new BackendFailure(response.status === 401 ? 401 : 502);
  }
  const reader = response.body.getReader();
  const chunks = []; let length = 0, complete = false;
  try {
    for (;;) {
      const next = await reader.read();
      if (next.done) { complete = true; break; }
      length += next.value.byteLength;
      if (length > MAX_BODY) throw new Error('Protected data exceeded response limit');
      if (chunks.length >= 1024) throw new Error('Protected data exceeded chunk count limit');
      chunks.push(next.value);
    }
    return new TextDecoder('utf-8', {fatal: true}).decode(Buffer.concat(chunks, length));
  } finally {
    try { if (!complete) await reader.cancel(); } finally { reader.releaseLock(); }
  }
}

function reply(response, status, body, type = 'text/plain; charset=utf-8') {
  response.writeHead(status, {'Content-Type': type, 'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff'});
  response.end(body);
}

const server = createServer(async (request, response) => {
  if (request.headers.host !== `127.0.0.1:${port}` || !request.url?.startsWith('/') || request.url.startsWith('//'))
    return reply(response, 400, 'Invalid request origin');
  if (request.headers['transfer-encoding'] || Number(request.headers['content-length'] || 0) !== 0) {
    response.setHeader('Connection', 'close');
    return reply(response, 413, 'Request body is not accepted');
  }
  let url;
  try { url = new URL(request.url, 'http://127.0.0.1'); }
  catch { return reply(response, 400, 'Invalid request URL'); }
  if (url.pathname.startsWith('/__proof/')) {
    if (!controlAuthorized(request)) return reply(response, 403, 'Forbidden');
    if (request.method === 'GET' && url.pathname === '/__proof/state') {
      return reply(response, 200, JSON.stringify({active: active.size, requests: [...history.values()]}), 'application/json');
    }
    if (request.method === 'POST' && url.pathname === '/__proof/release') {
      const operation = active.get(url.searchParams.get('id'));
      if (!operation?.render) return reply(response, 409, 'No ready request');
      operation.render.release();
      return reply(response, 204, '');
    }
    return reply(response, 404, 'Not found');
  }
  if (request.method !== 'GET') return reply(response, 405, 'Method not allowed');
  if (url.pathname === '/ready') return reply(response, 200, 'Ready');
  if (url.pathname === '/assets/application.js') return reply(response, 200, application, 'text/javascript; charset=utf-8');
  if (url.pathname !== '/ssr/todos') return reply(response, 404, 'Not found');
  const sessionCookie = cookie(request);
  if (!sessionCookie) return reply(response, 401, 'Authentication required');
  const id = url.searchParams.get('id');
  if (!id || !/^[A-Za-z0-9-]{1,40}$/.test(id)) return reply(response, 400, 'A bounded proof request ID is required');
  if (active.has(id) || active.size >= 16) return reply(response, 409, 'Request capacity reached');
  if (url.searchParams.has('hold') && !controlAuthorized(request)) return reply(response, 403, 'Forbidden');

  const controller = new AbortController();
  const metrics = {id, sessionReads: 0, dataReads: 0, ready: false, finished: false, aborted: false, timedOut: false};
  const operation = {controller, render: null};
  history.delete(id); history.set(id, metrics);
  while (history.size > 64) history.delete(history.keys().next().value);
  active.set(id, operation);
  const stop = () => {
    if (response.writableFinished) return;
    metrics.aborted = true;
    controller.abort();
    operation.render?.abort();
  };
  response.once('close', stop);
  const deadline = setTimeout(() => {
    metrics.timedOut = true;
    controller.abort();
    operation.render?.abort();
  }, 15_000);
  try {
    metrics.sessionReads++;
    const session = await data(renderer.sessionPath, sessionCookie, controller.signal);
    // The second read is independently authenticated by Spring. A logout or
    // session replacement between calls must fail, never reuse another identity.
    metrics.dataReads++;
    const todos = await data(renderer.todosPath, sessionCookie, controller.signal);
    if (controller.signal.aborted) throw new Error('SSR request cancelled');
    operation.render = renderer.open(session, todos);
    // Both readiness promises are observed even if the shell fails first.
    operation.render.shellReady.catch(() => {});
    operation.render.allReady.catch(() => {});
    await operation.render.shellReady;
    metrics.ready = true;
    if (!url.searchParams.has('hold')) operation.render.release();
    await operation.render.allReady;
    if (controller.signal.aborted || operation.render.errors()) throw new Error('SSR render did not complete');
    response.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'private, no-store', Vary: 'Cookie',
      'X-Content-Type-Options': 'nosniff',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; img-src 'self' data:; frame-ancestors 'none'"
    });
    const delivered = finished(response, {cleanup: true});
    delivered.catch(() => {});
    operation.render.pipe(response);
    await delivered;
    metrics.finished = true;
  } catch (error) {
    if (!response.destroyed) {
      if (response.headersSent) response.destroy();
      else reply(response, metrics.timedOut ? 504 : error instanceof BackendFailure ? error.status : 502,
        'Protected rendering failed');
    }
  } finally {
    clearTimeout(deadline);
    response.off('close', stop);
    operation.render?.abort();
    controller.abort();
    active.delete(id);
  }
});

server.maxConnections = 64;
server.requestTimeout = 20_000;
server.headersTimeout = 10_000;
server.keepAliveTimeout = 1000;
server.listen(port, '127.0.0.1', () => console.log('Protected SSR proof listening on loopback port', port));
function shutdown() {
  for (const operation of active.values()) { operation.controller.abort(); operation.render?.abort(); }
  server.close(() => process.exit(0));
  server.closeAllConnections();
}
process.once('SIGTERM', shutdown);
process.once('SIGINT', shutdown);
