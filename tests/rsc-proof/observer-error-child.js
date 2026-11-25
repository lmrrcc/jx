import assert from 'node:assert/strict';
import {setImmediate} from 'node:timers/promises';
import {server} from './target/server.cjs';

// A real child process keeps the intentional uncaught exception isolated from
// node:test. Do not replace reportError: this must exercise Node's actual host.
assert.equal(typeof globalThis.reportError, 'undefined', 'Node fallback must be active');
const events = [], reported = [], unhandled = [];
let reportedOnce;
const errorReported = new Promise(resolve => { reportedOnce = resolve; });
const onException = (error, origin) => {
  reported.push({error, origin});
  events.push('uncaught');
  reportedOnce();
};
const onRejection = reason => { unhandled.push(reason); };
process.on('uncaughtException', onException);
process.on('unhandledRejection', onRejection);
try {
  const fixture = server.observerError(event => events.push(event));
  const thenable = fixture.thenable;
  assert.equal(thenable, fixture.again(), 'React.thenable must retain native identity');
  const delivered = thenable.then(value => {
    events.push('native');
    return value;
  });
  fixture.resolve();
  assert.deepEqual(events, [], 'settlement must not deliver observers synchronously');
  queueMicrotask(() => events.push('after settlement'));
  const [value] = await Promise.all([delivered, errorReported]);
  await setImmediate(); // complete the host turn, including unhandled-rejection reporting
  assert.equal(value, fixture.value);
  assert.equal(reported.length, 1, 'the throwing observer must be reported exactly once');
  assert.equal(reported[0].error, fixture.failure(), 'report the original Java exception object');
  assert.equal(reported[0].origin, 'uncaughtException');
  assert.match(reported[0].error.message, /node observer failed/);
  assert.deepEqual(unhandled, [], 'the internal reaction must not leak an unhandled rejection');
  assert.deepEqual(events, ['throwing', 'sibling', 'after settlement', 'uncaught', 'native']);
  assert.equal(thenable, fixture.again());
  const before = [...events];
  fixture.resolve();
  await setImmediate();
  assert.deepEqual(events, before, 'repeated settlement must not deliver or report twice');
  assert.equal(reported.length, 1);
  assert.deepEqual(unhandled, []);
  process.stdout.write(JSON.stringify({reported: reported.length, unhandled: unhandled.length,
    value, events, sameThenable: thenable === fixture.again()}) + '\n');
} finally {
  process.off('uncaughtException', onException);
  process.off('unhandledRejection', onRejection);
}
