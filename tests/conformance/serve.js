// Framework browser-test fixture, not an application server.
// Serves the reference build under /reference/ and the JX build under /jx/.
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {join, normalize, sep} from 'node:path';

const roots = {
  reference: ['tests/conformance/target/reference-site', 'tests/conformance/reference'],
  jx: ['tests/conformance/target/site'],
  'reference-lazy': ['tests/conformance/target/reference-lazy/site'],
  'jx-lazy': ['tests/conformance/target/jx-lazy/site']
};
const types = {'.html': 'text/html', '.js': 'text/javascript', '.map': 'application/json'};

const server = createServer(async (request, response) => {
  const parts = new URL(request.url, 'http://localhost').pathname.split('/').filter(Boolean);
  const directories = roots[parts[0]];
  const file = parts.length === 1 ? 'index.html' : parts.slice(1).join('/');
  const lazy = parts[0] === 'reference-lazy' || parts[0] === 'jx-lazy';
  const allowed = lazy
    ? /^(index\.html|(?:host|route|shared-[A-Z0-9]+)\.js(?:\.map)?)$/.test(file)
    : ['index.html', 'application.js', 'application.js.map'].includes(file);
  if (!directories || parts.length > 2 || !/^[A-Za-z0-9.-]+$/.test(file) || !allowed) {
    response.writeHead(404).end();
    return;
  }
  for (const directory of directories) {
    const path = lazy && file === 'index.html' ? 'tests/conformance/lazy-index.html' : normalize(join(directory, file));
    if (!(lazy && file === 'index.html') && !path.startsWith(normalize(directory) + sep)) continue;
    try {
      const body = await readFile(path);
      response.writeHead(200, {'Content-Type': types[file.slice(file.lastIndexOf('.'))], 'Cache-Control': 'no-store'}).end(body);
      return;
    } catch { /* try the next directory */ }
  }
  response.writeHead(503).end('Build the conformance bench first.');
});
// BENCH_PORT gives the mutation runner a separate fixture port.
server.listen(Number(process.env.BENCH_PORT || 4573), '127.0.0.1');
process.on('SIGTERM', () => server.close());
