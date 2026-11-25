// Loopback-only framework proof host. Applications and shared props on the JX
// side are Java. This process owns the real React server dispatcher and Flight IO.
import {createServer} from 'node:http';
import {readFile, stat} from 'node:fs/promises';
import {resolve, dirname} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {Transform} from 'node:stream';
import {createElement} from 'react';
import {renderToPipeableStream, registerClientReference} from 'react-server-dom-webpack/server.node';
import {server as java} from './target/server.cjs';
import {validateManifest} from './manifest.js';

const here = dirname(fileURLToPath(import.meta.url));
const site = resolve(here, 'target/site');
const port = Number(process.env.RSC_PORT || 59182);
const origin = 'http://127.0.0.1:' + port;
const modules = ['jx', 'reference'].map(side => pathToFileURL(resolve(here, side + '-client.js')).href);
const manifestPath = resolve(site, 'react-client-manifest.json');
if ((await stat(manifestPath)).size > 65536) throw new Error('Oversized proof client manifest');
const manifest = validateManifest(JSON.parse(await readFile(manifestPath, 'utf8')), modules);
const references = Object.fromEntries(['jx', 'reference'].map((side, index) => {
  const module = modules[index];
  return [side, registerClientReference(() => { throw new Error('Client reference invoked on server'); }, module, 'Counter')];
}));
function ReferencePage({request}) {
  return createElement('main', null, createElement('h1', null, 'Java Server Component'),
    createElement('p', {role: 'status'}, request), createElement(references.reference, {initial: 5, label: request}));
}
let active = 0;
const http = createServer(async (request, response) => {
  try {
    if (request.method !== 'GET' || request.headers.host !== '127.0.0.1:' + port ||
        !request.url.startsWith('/') || request.url.length > 2048) {
      response.writeHead(400).end('Invalid request'); return;
    }
    const url = new URL(request.url, origin);
    if (url.pathname === '/') {
      response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'});
      response.end('<!doctype html><html lang="en"><head><meta charset="utf-8"><link rel="icon" href="data:,"><title>JX Flight proof</title></head><body><div id="app"></div><script src="/assets/application.js"></script></body></html>');
      return;
    }
    if (url.pathname.startsWith('/assets/')) {
      const name = url.pathname.slice('/assets/'.length);
      if (!/^[a-zA-Z0-9_.-]+\.(js|map)$/.test(name)) { response.writeHead(404).end(); return; }
      const path = resolve(site, name);
      if ((await stat(path)).size > 16 * 1024 * 1024) throw new Error('Oversized proof asset');
      response.writeHead(200, {'Content-Type': name.endsWith('.js') ? 'text/javascript' : 'application/json'});
      response.end(await readFile(path)); return;
    }
    if (url.pathname !== '/flight') { response.writeHead(404).end(); return; }
    const side = url.searchParams.get('side');
    const label = url.searchParams.get('request');
    if (!['jx', 'reference'].includes(side) || !/^[a-z]{1,32}$/.test(label || '')) {
      response.writeHead(400).end('Invalid proof props'); return;
    }
    if (active >= 8) { response.writeHead(503).end('Proof is busy'); return; }
    active++;
    let bytes = 0;
    let closed = false;
    let stream;
    const output = new Transform({transform(chunk, encoding, complete) {
      bytes += chunk.length;
      complete(bytes > 1024 * 1024 ? new Error('Flight output limit exceeded') : null, chunk);
    }});
    const cleanup = () => {
      if (closed) return;
      closed = true;
      clearTimeout(deadline);
      active--;
      stream?.abort();
      output.destroy();
    };
    const deadline = setTimeout(() => { response.destroy(); cleanup(); }, 10000);
    response.once('close', cleanup);
    response.once('error', cleanup);
    output.once('error', error => response.destroy(error));
    try {
      const tree = side === 'jx' ? java.render(label, references.jx, null) : createElement(ReferencePage, {request: label});
      stream = renderToPipeableStream(tree, manifest, {onError() { return 'RSC_PROOF_FAILED'; }});
      response.writeHead(200, {'Content-Type': 'text/x-component', 'Cache-Control': 'no-store'});
      output.pipe(response);
      stream.pipe(output);
    } catch (error) {
      cleanup();
      if (!response.headersSent) response.writeHead(500).end('Flight render failed');
      else response.destroy(error);
    }
  } catch {
    if (!response.headersSent) response.writeHead(404).end();
    else response.destroy();
  }
});
http.listen(port, '127.0.0.1');
process.once('SIGTERM', () => http.close());
