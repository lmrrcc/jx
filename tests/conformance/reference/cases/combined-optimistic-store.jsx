import {startTransition, useMemo, useOptimistic, useState, useSyncExternalStore, useTransition} from 'react';

class RequestFailure extends Error {}

const item = (id, label, optimistic) => Object.freeze({id, label, optimistic});
const appendMissing = (current, added) => current.some(existing => existing.id === added.id)
  ? current : Object.freeze([...current, added]);

function createStore() {
  const initial = Object.freeze([item('seed', 'Seed', false)]);
  let value = initial;
  const listeners = new Set();
  let active = null;
  let closed = false;
  const requireOpen = () => {
    if (closed) throw new Error('The page fixture is closed');
  };
  const request = () => {
    requireOpen();
    if (active === null) throw new Error('No request is pending');
    return active;
  };
  const publish = added => {
    requireOpen();
    const next = appendMissing(value, added);
    if (next === value) return;
    value = next;
    for (const listener of [...listeners]) if (listeners.has(listener)) listener();
  };
  return {
    snapshot: () => value,
    serverSnapshot: () => initial,
    subscribe(listener) {
      requireOpen();
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    publishRemote: () => publish(item('remote', 'Remote', false)),
    commit(confirmed) {
      if (confirmed.optimistic) throw new Error('Cannot commit an optimistic item');
      publish(confirmed);
    },
    begin() {
      requireOpen();
      if (active !== null) throw new Error('A request is already pending');
      const attempt = {};
      attempt.response = new Promise((resolve, reject) => { attempt.accept = resolve; attempt.reject = reject; });
      attempt.completion = new Promise(resolve => { attempt.finish = resolve; });
      active = attempt;
      return attempt;
    },
    accept: () => request().accept(item('local', 'Local', false)),
    reject: () => request().reject(new RequestFailure('rejected')),
    finishAction: () => request().finish(),
    end(attempt) {
      if (active !== attempt) throw new Error('Request ownership changed');
      // Rejection skips the second await, so settle that unused gate as well.
      attempt.finish();
      active = null;
    },
    subscribers: () => listeners.size,
    requests: () => active === null ? 0 : 1,
    closed: () => closed,
    close() {
      // Explicit fixture-owner disposal, not automatic cancellation supplied by React.
      if (closed) return;
      closed = true;
      if (active !== null) {
        active.reject(new RequestFailure('closed'));
        active.finish();
      }
      // React owns listener cleanup; clearing this Set would conceal a subscription leak.
    }
  };
}

function Editor({store}) {
  const committed = useSyncExternalStore(store.subscribe, store.snapshot, store.serverSnapshot);
  const [shown, addOptimistic] = useOptimistic(committed, appendMissing);
  const [pending, start] = useTransition();
  const [outcome, setOutcome] = useState('idle');
  return (
    <section aria-label="Optimistic editor">
      <p data-testid="pending">{pending ? 'pending' : 'idle'}</p>
      <p role="status">{outcome}</p>
      <button disabled={pending} onClick={() => {
        setOutcome('waiting');
        start(async () => {
          const attempt = store.begin();
          addOptimistic(item('local', 'Local', true));
          try {
            const confirmed = await attempt.response;
            if (!store.closed()) {
              store.commit(confirmed);
              await attempt.completion;
              if (!store.closed()) startTransition(() => setOutcome('saved'));
            }
          } catch (error) {
            if (!(error instanceof RequestFailure)) throw error;
            if (!store.closed()) startTransition(() => setOutcome('failed ' + error.message));
          } finally {
            store.end(attempt);
          }
        });
      }}>Add optimistic item</button>
      <button disabled={!pending} onClick={() => store.accept()}>Accept request</button>
      <button disabled={!pending} onClick={() => store.reject()}>Reject request</button>
      <button disabled={!pending} onClick={() => store.finishAction()}>Finish action</button>
      <ul aria-label="Visible items">{shown.map(entry =>
        <li key={entry.id} data-id={entry.id} data-state={entry.optimistic ? 'optimistic' : 'confirmed'}>
          {entry.label}: {entry.optimistic ? 'sending' : 'saved'}
        </li>)}</ul>
      <ol aria-label="Committed items">{committed.map(entry => <li key={entry.id} data-id={entry.id}>{entry.label}</li>)}</ol>
    </section>
  );
}

export function CombinedOptimisticStore() {
  const store = useMemo(createStore, []);
  const [mounted, setMounted] = useState(true);
  const [ownership, setOwnership] = useState('uninspected');
  return (
    <main>
      <button disabled={!mounted} onClick={() => store.publishRemote()}>Publish remote item</button>
      <button onClick={() => setOwnership('subscribers=' + store.subscribers()
        + ';requests=' + store.requests() + ';closed=' + store.closed())}>Inspect ownership</button>
      <button disabled={!mounted} onClick={() => { store.close(); setMounted(false); }}>Close editor</button>
      <p data-testid="ownership">{ownership}</p>
      {mounted ? <Editor store={store} /> : null}
    </main>
  );
}
