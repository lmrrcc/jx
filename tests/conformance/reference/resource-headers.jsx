import {Writable} from 'node:stream';
import {preconnect, preload} from 'react-dom';
import {renderToPipeableStream, renderToReadableStream} from 'react-dom/server';
import {prerender, prerenderToNodeStream} from 'react-dom/static';

function ResourceHeaderPage({origin, hints}) {
  if (hints) {
    preconnect(origin);
    preconnect(origin);
    preload(origin + '/font.woff2', {as: 'font', crossOrigin: '', type: 'font/woff2'});
    preload(origin + '/hero.png', {as: 'image', fetchPriority: 'high'});
  }
  return <html><head/><body><h1>headers</h1></body></html>;
}

export async function resourceHeaders(kind, origin, limit, hints, fail) {
  const links = [], events = [], failure = new Error('header destination refused');
  const options = {
    onHeaders(headers) {
      links.push(typeof headers.get === 'function' ? headers.get('Link') : headers.Link ?? null);
      events.push('headers');
      if (fail) throw failure;
    },
    onError(error) { events.push(error === failure ? 'original-error' : 'unexpected-error'); }
  };
  if (limit !== -1) options.maxHeadersLength = limit;
  const page = <ResourceHeaderPage origin={origin} hints={hints}/>;
  let html;
  if (kind === 'readable') {
    const stream = await renderToReadableStream(page, options);
    events.push('ready');
    await stream.allReady;
    html = await new Response(stream).text();
  } else if (kind === 'pipeable') {
    html = await new Promise((resolve, reject) => {
      let text = '';
      const stream = renderToPipeableStream(page, {...options, onShellError: reject, onShellReady() {
        events.push('ready');
        stream.pipe(new Writable({write(chunk, encoding, done) { text += chunk.toString(); done(); },
          final(done) { resolve(text); done(); }}).on('error', reject));
      }});
    });
  } else if (kind === 'prerender') {
    const result = await prerender(page, options);
    events.push('ready');
    html = await new Response(result.prelude).text();
  } else if (kind === 'prerender-node') {
    const result = await prerenderToNodeStream(page, options);
    events.push('ready');
    html = '';
    for await (const chunk of result.prelude) html += chunk.toString();
  } else throw new Error('Unknown renderer: ' + kind);
  events.push('end');
  return {html, links, events};
}
