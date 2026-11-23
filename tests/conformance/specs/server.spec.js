import {expect} from '@playwright/test';
import {server as onServer, serverOnly} from './bench.js';

// React's server and static rendering APIs, run in Node on the reference side.
const withMarkers = '<main><h1>Hello <!-- -->Ada</h1><p>static</p></main>';
const resumed = result => {
  expect(result.postponed).toBe(true);
  expect(result.shell).toContain('<h1>shell</h1>');
  expect(result.shell).toContain('<p>waiting</p>');
  expect(result.shell).not.toContain('arrived');
  expect(result.rest).toContain('<p>data <!-- -->arrived</p>');
};

onServer('server-render-to-string: renders HTML with hydration markers', async server => {
  expect(await server.renderToString()).toEqual(withMarkers);
});

onServer('server-render-to-static-markup: renders HTML without React markers', async server => {
  expect(await server.renderToStaticMarkup()).toEqual('<main><h1>Hello Ada</h1><p>static</p></main>');
});

serverOnly('server-readable-stream: streams HTML as a web stream', async server => {
  expect(await server.renderToReadableStream()).toEqual(withMarkers);
});

serverOnly('server-pipeable-stream: streams HTML as a Node stream', async server => {
  expect(await server.renderToPipeableStream()).toEqual(withMarkers);
});

serverOnly('static-prerender: prerenders to a web stream after all data is ready', async server => {
  expect(await server.prerender()).toEqual(withMarkers);
});

serverOnly('static-prerender-node: prerenders to a Node stream', async server => {
  expect(await server.prerenderToNodeStream()).toEqual(withMarkers);
});

serverOnly('server-resume: continues an aborted prerender as a web stream', async server => {
  resumed(await server.resume());
});

serverOnly('server-resume-pipeable: continues an aborted prerender as a Node stream', async server => {
  resumed(await server.resumeToPipeableStream());
});

serverOnly('static-resume-prerender: finishes an aborted prerender statically', async server => {
  const result = await server.resumeAndPrerender();
  resumed(result);
  expect(result.finished).toBe(true);
});

serverOnly('static-resume-prerender-node: finishes an aborted prerender to a Node stream', async server => {
  const result = await server.resumeAndPrerenderToNodeStream();
  resumed(result);
  expect(result.finished).toBe(true);
});
