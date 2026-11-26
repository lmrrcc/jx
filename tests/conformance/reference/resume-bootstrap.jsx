import {Suspense, use} from 'react';
import {Writable} from 'node:stream';
import {prerender} from 'react-dom/static';
import {resume, resumeToPipeableStream} from 'react-dom/server';

function Child({data, nonce, entered}) {
  entered();
  const value = use(data);
  return <><style href="resume-style" precedence="app" nonce={nonce}>{'.resume{color:rgb(12, 34, 56)}'}</style>
    <p className="resume">{value}</p></>;
}
function Page({data, nonce, entered}) {
  return <html><head></head><body><h1>shell</h1><Suspense fallback={<p>waiting</p>}>
    <Child data={data} nonce={nonce} entered={entered}/></Suspense></body></html>;
}
export async function resumeBootstrap(kind, token) {
  let entered;
  const seen = new Promise(resolve => { entered = resolve; });
  const controller = new AbortController();
  const pending = prerender(<Page data={new Promise(() => {})} entered={() => queueMicrotask(entered)}/>, {
    signal: controller.signal, onError() {},
    bootstrapScriptContent: 'globalThis.__resumeBoot = (globalThis.__resumeBoot || 0) + 1;'
  });
  await seen;
  controller.abort();
  const first = await pending;
  if (!first.postponed) throw new Error('Missing postponed boundary');
  const shell = await new Response(first.prelude).text();
  const state = JSON.parse(JSON.stringify(first.postponed));
  const view = <Page data={Promise.resolve('arrived')} nonce={token + '-style'} entered={() => {}}/>;
  const options = {nonce: {script: token + '-script', style: token + '-style'},
    bootstrapScriptContent: "throw new Error('resume must not replace the original bootstrap');"};
  if (kind === 'readable') {
    const stream = await resume(view, state, options);
    await stream.allReady;
    return {shell, rest: await new Response(stream).text()};
  }
  if (kind !== 'pipeable') throw new Error('Unknown resume renderer: ' + kind);
  const rest = await new Promise((resolve, reject) => {
    let html = '';
    const stream = resumeToPipeableStream(view, state, {...options, onShellError: reject, onAllReady() {
      stream.pipe(new Writable({write(chunk, encoding, done) { html += chunk.toString(); done(); },
        final(done) { resolve(html); done(); }}).on('error', reject));
    }});
  });
  return {shell, rest};
}
