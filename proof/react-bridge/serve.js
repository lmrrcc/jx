// Local test fixture server, not the application backend or development tool.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
const assets = new Map([
  ['/', ['proof/react-bridge/index.html', 'text/html']],
  ['/application.js', ['proof/react-bridge/target/site/application.js', 'text/javascript']],
  ['/application.js.map', ['proof/react-bridge/target/site/application.js.map', 'application/json']]
]);
const server = createServer(async (req, res) => {
  const asset = assets.get(new URL(req.url, 'http://localhost').pathname);
  if (!asset) { res.writeHead(404).end(); return; }
  try { res.writeHead(200, {'Content-Type': asset[1]}).end(await readFile(asset[0])); }
  catch { res.writeHead(503).end('Build the proof first.'); }
});
server.listen(Number(process.env.JX_PROOF_PORT || 4173), '127.0.0.1');
process.on('SIGTERM', () => server.close());
