import {Writable} from 'node:stream';
import {renderToPipeableStream, renderToReadableStream} from 'react-dom/server';
import {prerender, prerenderToNodeStream} from 'react-dom/static';

export async function renderBootstrap(kind, mode, token) {
  const options = {
    bootstrapScriptContent: 'globalThis.__bootstrapValue = "</script><script>globalThis.__injected=1</script>";',
    bootstrapScripts: ['/bootstrap-classic.js'], bootstrapModules: ['/bootstrap-module.js']
  };
  if (mode === 'script') options.nonce = token + '-script';
  else if (mode === 'style') options.nonce = {style: token + '-style'};
  else if (mode === 'split') options.nonce = {script: token + '-script', style: token + '-style'};
  else if (mode !== 'none') throw new Error('Unknown nonce mode: ' + mode);
  const styleNonce = mode === 'style' || mode === 'split' ? token + '-style' : undefined;
  const page = <html><head><style href="bootstrap-style" precedence="app" nonce={styleNonce}>{'.bootstrap-probe{color:rgb(12, 34, 56)}'}</style></head>
    <body><h1 className="bootstrap-probe">bootstrap</h1></body></html>;
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
