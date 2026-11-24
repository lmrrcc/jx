// Framework browser-test fixture, not an application server.
// Serves the host page and the script files esbuild wrote, including the lazy chunk.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';

const server = createServer(async (request, response) => {
  const path = new URL(request.url, 'http://localhost').pathname;
  const file = path === '/' ? 'tests/lazy-boundary/index.html'
    : /^\/[A-Za-z0-9.-]+\.js(\.map)?$/.test(path) ? 'tests/lazy-boundary/target/site' + path : null;
  if (!file) { response.writeHead(404).end(); return; }
  const type = path === '/' ? 'text/html' : path.endsWith('.map') ? 'application/json' : 'text/javascript';
  try { response.writeHead(200, {'Content-Type': type, 'Cache-Control': 'no-store'}).end(await readFile(file)); }
  catch { response.writeHead(404).end(); }
});
server.listen(4577, '127.0.0.1');
process.on('SIGTERM', () => server.close());
