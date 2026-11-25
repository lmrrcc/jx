import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';

test('Node reports one original observer error while siblings and the cached thenable settle in order',
  {timeout: 10000}, async () => {
    const child = spawn(process.execPath, ['--conditions=react-server',
      fileURLToPath(new URL('./observer-error-child.js', import.meta.url))],
    {stdio: ['ignore', 'pipe', 'pipe']});
    let stdout = '', stderr = '', bytes = 0, limitFailure;
    const capture = target => chunk => {
      bytes += chunk.length;
      if (bytes > 65536) {
        limitFailure = new Error('Observer child exceeded its output limit');
        child.kill('SIGKILL');
      } else if (target === 'out') stdout += chunk.toString('utf8');
      else stderr += chunk.toString('utf8');
    };
    child.stdout.on('data', capture('out'));
    child.stderr.on('data', capture('err'));
    const timer = setTimeout(() => {
      limitFailure = new Error('Observer child did not settle within 8 seconds');
      child.kill('SIGKILL');
    }, 8000);
    try {
      const result = await new Promise((resolve, reject) => {
        child.once('error', reject);
        child.once('close', (code, signal) => resolve({code, signal}));
      });
      if (limitFailure) throw limitFailure;
      assert.equal(result.signal, null, stderr);
      assert.equal(result.code, 0, stderr);
      assert.equal(stderr, '');
      const resultData = JSON.parse(stdout);
      assert.deepEqual(resultData, {
        reported: 1, unhandled: 0, value: 'kept', sameThenable: true,
        events: ['throwing', 'sibling', 'after settlement', 'uncaught', 'native']
      });
    } finally {
      clearTimeout(timer);
      if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
    }
  });
