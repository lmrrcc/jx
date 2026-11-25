// Explicit React reference fixture for the Java cache contract, not application code.
import {cache, cacheSignal, createElement, use} from 'react';
import assert from 'node:assert/strict';
let sequence = 0;
let nullComputations = 0;
function token() { assert.ok(sequence < 10000); return {id: ++sequence}; }
const byKey = cache(() => token());
const zero = cache(() => token());
const two = cache(() => token());
const body = () => token();
const first = cache(body), second = cache(body);
const separate = cache(() => token());
const failure = cache(() => { throw new Error('cached-failure-' + token().id); });
const asynchronous = cache(() => Promise.resolve(token()));
const nullValue = cache(() => { nullComputations++; return null; });
let recursiveCalls = 0;
const recursive = cache(value => {
  recursiveCalls++;
  if (value < 0) throw new Error('Nested fixture failure');
  if (value === 0) return 0;
  if (value === 2) assert.throws(() => recursive(-1), /Nested fixture failure/);
  return value + recursive(value - 1);
});
function failed() { try { failure('failure'); } catch (error) { return error; } throw new Error('Failure became success'); }
function Page({audit, data, ready}) {
  assert.ok(++audit.renders <= 20);
  const shared = byKey('shared');
  assert.equal(shared, byKey('sha' + 'red'));
  if (audit.token !== 0) assert.equal(audit.token, shared.id);
  audit.token = shared.id;
  assert.equal(zero(), zero());
  assert.equal(two('pair', 1), two('pair', 1));
  assert.notEqual(two('pair', 1), two('pair', 2));
  assert.equal(first('same'), second('same'));
  assert.notEqual(first('same'), separate('same'));
  assert.equal(byKey(null), byKey(null));
  assert.equal(nullValue('null'), null);
  const nullCount = nullComputations;
  assert.equal(nullValue('null'), null);
  assert.equal(nullComputations, nullCount);
  assert.equal(byKey(NaN), byKey(NaN));
  assert.equal(byKey(0), byKey(-0));
  assert.equal(byKey(7), byKey(7.0));
  const object = {value: 5};
  assert.equal(byKey(object), byKey(object));
  assert.notEqual(byKey(object), byKey({value: 5}));
  assert.equal(failed(), failed());
  assert.equal(recursive(6), 21);
  const calls = recursiveCalls;
  assert.equal(recursive(6), 21);
  assert.equal(recursiveCalls, calls);
  const signal = cacheSignal();
  assert.ok(signal && !signal.aborted);
  if (audit.signal === null) {
    audit.signal = signal;
    const onAbort = () => { audit.aborts++; };
    signal.addEventListener('abort', onAbort, {once: true});
    audit.cleanup = () => signal.removeEventListener('abort', onAbort);
    const removed = () => { audit.removed++; };
    signal.addEventListener('abort', removed, {once: true});
    signal.removeEventListener('abort', removed);
    signal.removeEventListener('abort', removed);
  } else assert.equal(signal, audit.signal);
  const promise = asynchronous('async');
  assert.equal(promise, asynchronous('async'));
  audit.async = use(promise).id;
  if (!audit.checked) { audit.checked = true; ready(); }
  audit.data = use(data);
  return createElement('p', {'data-testid': 'cache-result'}, audit.token, ':', audit.data);
}
export const reference = {
  outside() {
    const before = sequence;
    const unused = cache(() => token());
    return sequence === before && unused !== null && cacheSignal() === null && byKey('outside') !== byKey('outside') && zero() !== zero();
  },
  cache(data, ready) {
    const audit = {token: 0, async: 0, signal: null, aborts: 0, removed: 0, late: 0, renders: 0, checked: false, data: 'pending', cleanup: null};
    return {
      element: createElement(Page, {audit, data, ready}),
      snapshot: () => ({token: audit.token, async: audit.async, aborted: audit.signal?.aborted || false, aborts: audit.aborts,
        removed: audit.removed, late: audit.late, renders: audit.renders, checked: audit.checked, data: audit.data}),
      late() { assert.ok(audit.signal.aborted); audit.late++; },
      close() { audit.cleanup?.(); audit.cleanup = null; }
    };
  }
};
