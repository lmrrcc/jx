// Framework browser-test fixture, not an application server.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
const assets = new Map([
  ['/', ['tests/runtime-browser/index.html', 'text/html']],
  ['/hot', ['tests/runtime-browser/hot.html', 'text/html']],
  ['/application.js', ['tests/runtime-browser/target/site/application.js', 'text/javascript']],
  ['/application.js.map', ['tests/runtime-browser/target/site/application.js.map', 'application/json']]
]);
const resourceRequests = new Set();
let resourceClosed = 0;
const server = createServer(async (req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname;
  if (path === '/api/resource-state') {
    res.writeHead(200, {'Content-Type': 'application/json'}).end(JSON.stringify({active: resourceRequests.size, closed: resourceClosed})); return;
  }
  if (path === '/api/resource-hold') {
    if (resourceRequests.size >= 8) {res.writeHead(503).end(); return;}
    resourceRequests.add(res);
    const deadline = setTimeout(() => res.destroy(), 10_000);
    res.once('close', () => {clearTimeout(deadline); resourceRequests.delete(res); resourceClosed++;});
    return;
  }
  if (path === '/api/value') {res.writeHead(200, {'Content-Type': 'application/json'}).end('{"title":"ready","version":7,"completed":true,"items":[{"title":"nested"}]}'); return;}
  if (path === '/api/error') {res.writeHead(409, {'Content-Type': 'application/problem+json'}).end('{"detail":"conflict"}'); return;}
  if (path === '/api/invalid') {res.writeHead(200, {'Content-Type': 'application/json'}).end('invalid'); return;}
  if (path === '/api/decode') {res.writeHead(200, {'Content-Type': 'application/json'}).end('{"title":12}'); return;}
  if (path === '/api/empty') {res.writeHead(204).end(); return;}
  if (path === '/api/slow') {setTimeout(() => res.writeHead(200, {'Content-Type': 'application/json'}).end('{"title":"late"}'), 100); return;}
  if (path === '/api/echo') {
    let body = ''; for await (const chunk of req) {body += chunk; if (body.length > 1024) {res.writeHead(413).end(); return;}}
    res.writeHead(201, {'Content-Type': 'application/json'}).end(body); return;
  }
  const asset = assets.get(path) || (path.startsWith('/todos/') ? assets.get('/') : null);
  if (!asset) {res.writeHead(404).end(); return;}
  try {res.writeHead(200, {'Content-Type': asset[1]}).end(await readFile(asset[0]));}
  catch {res.writeHead(503).end('Build the runtime fixture first.');}
});
server.listen(Number(process.env.RUNTIME_PORT || 4373), '127.0.0.1');
process.on('SIGTERM', () => {for (const response of resourceRequests) response.destroy(); server.close();});
