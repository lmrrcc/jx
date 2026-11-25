import {expect} from '@playwright/test';
import {server as onServer} from './bench.js';

onServer('server-fatal-readiness: fatal shell errors reject both readiness consumers without claiming complete output', async server => {
  const stream = server.startFatalStream();
  const observed = {shell: 'pending', all: 'pending'};
  const failures = {};
  const watch = (name, promise) => promise.then(
    () => { observed[name] = 'fulfilled'; },
    error => { observed[name] = 'rejected'; failures[name] = error; });
  // Attach both observers before rendering starts; rejected promises must never
  // become an unhandled rejection while the other consumer is being inspected.
  const shell = watch('shell', stream.shellReady);
  const all = watch('all', stream.allReady);
  try {
    await expect.poll(() => observed.shell).toBe('rejected');
    await expect.poll(() => observed.all).toBe('rejected');
    await Promise.all([shell, all]);
    for (const name of ['shell', 'all']) {
      expect(String(failures[name]?.message ?? failures[name])).toContain('fatal shell failed');
    }
    expect(stream.errors()).toBe(1);
  } finally {
    stream.abort();
  }
});
