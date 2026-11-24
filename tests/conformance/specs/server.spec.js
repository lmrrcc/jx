import {expect} from '@playwright/test';
import {server as onServer, interaction} from './bench.js';

// React's server and static rendering APIs, run in Node on the reference side.
const withMarkers = '<main><h1>Hello <!-- -->Ada</h1><p>static</p></main>';
const resumed = result => {
  expect(result.postponed).toBe(true);
  expect(result.shell).toContain('<h1>shell</h1>');
  expect(result.shell).toContain('<p>waiting</p>');
  expect(result.shell).not.toContain('arrived');
  expect(result.rest).toContain('<p>data <!-- -->arrived</p>');
  // Resuming sends only what was left out; rendering the page again would repeat the shell.
  expect(result.rest).not.toContain('<h1>shell</h1>');
};

onServer('server-render-to-string: renders HTML with hydration markers', async server => {
  expect(await server.renderToString()).toEqual(withMarkers);
});

onServer('server-render-to-static-markup: renders HTML without React markers', async server => {
  expect(await server.renderToStaticMarkup()).toEqual('<main><h1>Hello Ada</h1><p>static</p></main>');
});

onServer('server-readable-stream: streams HTML as a web stream', async server => {
  expect(await server.renderToReadableStream()).toEqual(withMarkers);
});

onServer('server-pipeable-stream: streams HTML as a Node stream', async server => {
  expect(await server.renderToPipeableStream()).toEqual(withMarkers);
});

onServer('server-stream-suspense: a boundary sends its fallback first and its content later', async server => {
  await interaction('stream-suspense', async () => {
    const html = await server.streamSuspense();
    expect(html).toContain('<h1>shell</h1>');
    const fallback = html.indexOf('<p>waiting</p>');
    const content = html.indexOf('arrived');
    expect(fallback).toBeGreaterThan(-1);
    expect(content).toBeGreaterThan(fallback);
    // The late content travels in a hidden element with the script that moves it into place.
    expect(html.slice(fallback)).toContain('hidden');
  });
});

onServer('static-prerender: prerenders to a web stream after all data is ready', async server => {
  expect(await server.prerender()).toEqual(withMarkers);
});

onServer('static-prerender-node: prerenders to a Node stream', async server => {
  expect(await server.prerenderToNodeStream()).toEqual(withMarkers);
});

onServer('server-resume: continues an aborted prerender as a web stream', async server => {
  resumed(await server.resume());
});

onServer('server-resume-pipeable: continues an aborted prerender as a Node stream', async server => {
  resumed(await server.resumeToPipeableStream());
});

onServer('static-resume-prerender: finishes an aborted prerender statically', async server => {
  const result = await server.resumeAndPrerender();
  resumed(result);
  expect(result.finished).toBe(true);
});

onServer('static-resume-prerender-node: finishes an aborted prerender to a Node stream', async server => {
  const result = await server.resumeAndPrerenderToNodeStream();
  resumed(result);
  expect(result.finished).toBe(true);
});
