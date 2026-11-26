// Framework-owned reference fixture: React's server and static rendering APIs, run in Node.
import { StrictMode, Suspense, use, version as reactVersion } from 'react';
import {version as domVersion} from 'react-dom';
import {version as clientVersion} from 'react-dom/client';
import {version as serverVersion} from 'react-dom/server';
import {version as staticVersion} from 'react-dom/static';
import { HydratedPage } from './cases/hydrate-server.jsx';
import { cases } from './cases.jsx';
import { combinedServer } from './combined-server.jsx';
import {renderBootstrap} from './bootstrap.jsx';
import {resumeBootstrap} from './resume-bootstrap.jsx';
import {resourceHeaders} from './resource-headers.jsx';
import { Writable } from 'node:stream';
import {
  renderToPipeableStream, renderToReadableStream, renderToStaticMarkup, renderToString,
  resume, resumeToPipeableStream
} from 'react-dom/server';
import { prerender, prerenderToNodeStream, resumeAndPrerender, resumeAndPrerenderToNodeStream } from 'react-dom/static';

function Page({ name }) {
  return <main><h1>Hello {name}</h1><p>static</p></main>;
}

async function readWeb(stream) {
  let text = '';
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) return text + decoder.decode();
    text += decoder.decode(value, { stream: true });
  }
}

function readNode(pipe) {
  return new Promise((resolve, reject) => {
    let text = '';
    const sink = new Writable({
      write(chunk, encoding, callback) { text += chunk.toString(); callback(); },
      final(callback) { resolve(text); callback(); }
    });
    sink.on('error', reject);
    pipe(sink);
  });
}

// A page whose second part waits for data. Prerendering is aborted while it waits,
// which yields a postponed state that the resume APIs continue from.
let data = new Promise(() => {});
function Slow() {
  return <p>data {use(data)}</p>;
}
function Postponing() {
  return <main><h1>shell</h1><Suspense fallback={<p>waiting</p>}><Slow /></Suspense></main>;
}
async function postponed(render) {
  data = new Promise(() => {});
  const controller = new AbortController();
  const pending = render(<Postponing />, { signal: controller.signal, onError() {} });
  await new Promise(resolve => setTimeout(resolve, 20));
  controller.abort();
  const result = await pending;
  data = Promise.resolve('arrived');
  return result;
}

// A boundary whose content arrives after the shell was sent.
let late = new Promise(() => {});
function Late() {
  return <p>data {use(late)}</p>;
}
function Streaming() {
  return <main><h1>shell</h1><Suspense fallback={<p>waiting</p>}><Late /></Suspense></main>;
}

export const server = {
  renderBootstrap,
  resumeBootstrap,
  resourceHeaders,
  ...combinedServer,
  reactVersion: () => reactVersion,
  domVersion: () => domVersion,
  clientVersion: () => clientVersion,
  serverVersion: () => serverVersion,
  staticVersion: () => staticVersion,
  // Any client case as static HTML, for the static mode of the bench.
  renderStatic: async id => {
    const Case = cases[id];
    return renderToStaticMarkup(<StrictMode><Case /></StrictMode>);
  },
  // Any client case as server HTML, for the hydrate mode of the bench.
  renderCase: async id => {
    const Case = cases[id];
    return renderToString(<StrictMode><Case /></StrictMode>);
  },
  streamSuspense: () => new Promise((resolve, reject) => {
    let release = () => {};
    late = new Promise(done => { release = done; });
    const stream = renderToPipeableStream(<Streaming />, {
      onShellReady() {
        readNode(sink => stream.pipe(sink)).then(resolve, reject);
        setTimeout(() => release('arrived'), 20);
      },
      onError: reject
    });
  }),
  renderHydratable: async () => renderToString(<StrictMode><HydratedPage /></StrictMode>),
  renderToString: async () => renderToString(<Page name="Ada" />),
  renderToStaticMarkup: async () => renderToStaticMarkup(<Page name="Ada" />),
  renderToReadableStream: async () => {
    const stream = await renderToReadableStream(<Page name="Ada" />);
    await stream.allReady;
    return readWeb(stream);
  },
  renderToPipeableStream: () => new Promise((resolve, reject) => {
    const stream = renderToPipeableStream(<Page name="Ada" />, {
      onAllReady() { readNode(sink => stream.pipe(sink)).then(resolve, reject); },
      onError: reject
    });
  }),
  prerender: async () => readWeb((await prerender(<Page name="Ada" />)).prelude),
  prerenderToNodeStream: async () => {
    const { prelude } = await prerenderToNodeStream(<Page name="Ada" />);
    return readNode(sink => prelude.pipe(sink));
  },
  resume: async () => {
    const first = await postponed(prerender);
    const shell = await readWeb(first.prelude);
    const stream = await resume(<Postponing />, first.postponed);
    await stream.allReady;
    return { shell, postponed: first.postponed !== null, rest: await readWeb(stream) };
  },
  resumeToPipeableStream: async () => {
    const first = await postponed(prerender);
    const shell = await readWeb(first.prelude);
    const rest = await new Promise((resolve, reject) => {
      const stream = resumeToPipeableStream(<Postponing />, first.postponed, {
        onAllReady() { readNode(sink => stream.pipe(sink)).then(resolve, reject); },
        onError: reject
      });
    });
    return { shell, postponed: first.postponed !== null, rest };
  },
  resumeAndPrerender: async () => {
    const first = await postponed(prerender);
    const shell = await readWeb(first.prelude);
    const second = await resumeAndPrerender(<Postponing />, first.postponed);
    return { shell, postponed: first.postponed !== null, rest: await readWeb(second.prelude), finished: second.postponed === null };
  },
  resumeAndPrerenderToNodeStream: async () => {
    const first = await postponed(prerender);
    const shell = await readWeb(first.prelude);
    const second = await resumeAndPrerenderToNodeStream(<Postponing />, first.postponed);
    return { shell, postponed: first.postponed !== null, rest: await readNode(sink => second.prelude.pipe(sink)), finished: second.postponed === null };
  }
};
