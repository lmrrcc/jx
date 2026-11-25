import {expect} from '@playwright/test';
import {server as onServer} from './bench.js';

onServer('server-sink-completion: terminal write and end failures reject delivery while another request completes', async server => {
  const payload = 'arrived-è-😀' + 'è😀'.repeat(900);
  for (const mode of ['first', 'late', 'end', 'success', 'abort']) {
    const request = server.startSinkCompletion(mode);
    const sibling = server.startSinkCompletion('success');
    let outcome = 'pending';
    let failure;
    try {
      await Promise.all([request.shellReady, sibling.shellReady]);
      const delivered = request.pipe().then(() => { outcome = 'fulfilled'; }, error => {
        failure = error;
        outcome = 'rejected';
      });
      const siblingDone = sibling.pipe();
      if (mode === 'success') await expect(request.pipe()).rejects.toThrow('only supports piping to one writable stream');
      if (mode !== 'first') await expect.poll(request.html).toContain('waiting-sink');
      if (mode === 'abort') request.abort();
      else if (mode !== 'first') request.release();
      const fails = ['first', 'late', 'end'].includes(mode);
      await expect.poll(() => outcome).toBe(fails ? 'rejected' : 'fulfilled');
      await delivered;
      expect(request.ends()).toBe(mode === 'first' || mode === 'late' ? 0 : 1);
      if (fails) {
        expect(String(failure?.message ?? failure)).toContain('sink failed');
        await expect.poll(request.originalFailure).toBe(true);
      } else if (mode === 'success') expect(request.html()).toContain(payload);
      else expect(request.html()).not.toContain('data-testid="arrived"');
      const stopped = [request.writes(), request.ends(), request.html()];
      // A closed/failed destination cannot receive late application data, and a
      // separate request must still complete with its own full UTF-8 payload.
      request.release();
      sibling.release();
      await siblingDone;
      expect(sibling.html()).toContain(payload);
      expect(sibling.ends()).toBe(1);
      expect([request.writes(), request.ends(), request.html()]).toEqual(stopped);
    } finally {
      request.abort();
      sibling.abort();
    }
  }
});
