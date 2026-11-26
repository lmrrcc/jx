import {Writable} from 'node:stream';
import {renderToPipeableStream, renderToReadableStream} from 'react-dom/server';
import {prerender, prerenderToNodeStream} from 'react-dom/static';

export async function renderImportMap(kind, token, integrity) {
  const imports = Object.create(null);
  imports.shared = '/maps/' + token + '/global.mjs';
  imports['pkg/'] = '/maps/pkg/';
  imports.__proto__ = '/maps/proto.mjs';
  imports.constructor = '/maps/constructor.mjs';
  imports['escaped</script><script>globalThis.__injected=1</script>'] = '/maps/unused.mjs';
  imports.blocked = null;
  imports['empty-shared'] = '/maps/fallback.mjs';
  imports['scoped-blocked'] = '/maps/should-not-load.mjs';
  const options = {importMap: {imports,
    scopes: {'/scope/': {shared: '/maps/' + token + '/scoped.mjs', 'scoped-blocked': null},
      '': {'empty-shared': '/maps/empty.mjs'}},
    integrity: {['/maps/' + token + '/global.mjs']: integrity}}, bootstrapModules: ['/map-entry.mjs'],
    bootstrapScriptContent: "import('empty-shared').then(m => { globalThis.__emptyScope = m.default; });"};
  if (!kind.startsWith('prerender')) options.nonce = token;
  const page = <html><head/><body><h1>import maps</h1></body></html>;
  if (kind === 'readable') {
    const stream = await renderToReadableStream(page, options);
    await stream.allReady;
    return new Response(stream).text();
  }
  if (kind === 'pipeable') return new Promise((resolve, reject) => {
    let html = '';
    const stream = renderToPipeableStream(page, {...options, onShellError: reject, onAllReady() {
      stream.pipe(new Writable({write(chunk, encoding, done) { html += chunk.toString(); done(); },
        final(done) { resolve(html); done(); }}).on('error', reject));
    }});
  });
  if (kind === 'prerender') return new Response((await prerender(page, options)).prelude).text();
  if (kind === 'prerender-node') {
    let html = '';
    for await (const chunk of (await prerenderToNodeStream(page, options)).prelude) html += chunk.toString();
    return html;
  }
  throw new Error('Unknown renderer: ' + kind);
}
