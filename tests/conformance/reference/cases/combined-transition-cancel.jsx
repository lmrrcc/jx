import {startTransition, useEffect, useRef, useState, useTransition} from 'react';

const requests = ['A', 'B', 'C', 'OLD', 'FRESH', 'RACE_OLD', 'RACE_NEW'];

// Fixture-local gates and ownership, not a cancellation API supplied by React or JX.
class Transport {
  gates = new Map();

  start(request) {
    if (this.gates.has(request)) throw new Error('Each fixture request starts once: ' + request);
    const gate = {completed: false};
    gate.promise = new Promise(resolve => { gate.resolve = resolve; });
    this.gates.set(request, gate);
    return gate.promise;
  }

  started(request) { return this.gates.has(request); }
  pending(request) { return this.started(request) && !this.gates.get(request).completed; }
  status(request) { return !this.started(request) ? 'not started' : this.pending(request) ? 'pending' : 'completed'; }
  complete(request) {
    const gate = this.gates.get(request);
    if (!gate) throw new Error('The transport gate has not started: ' + request);
    if (gate.completed) throw new Error('The transport gate was already completed');
    gate.completed = true;
    gate.resolve(request);
  }

  // This subscriber is registered after the owner's forwarding callback, so
  // it runs before the consumer continuation that callback queues on settlement.
  afterCompletion(request, action) {
    const gate = this.gates.get(request);
    if (!gate) throw new Error('The transport gate has not started: ' + request);
    gate.promise.then(action, failure => { throw new Error('Fixture transport cannot reject', {cause: failure}); });
  }
}

class Owner {
  mounted = false;
  current = null;

  activate() { this.mounted = true; }
  dispose() { this.mounted = false; this.cancel(); }

  begin(request, transport, audit) {
    if (!this.mounted) throw new Error('The operation owner is not mounted');
    this.cancel();
    const operation = {request};
    operation.consumer = new Promise((resolve, reject) => {
      operation.resolve = resolve;
      operation.reject = reject;
    });
    this.current = operation;
    transport.start(request).then(value => {
      audit('transport ' + request + ' completed');
      if (this.owns(operation)) operation.resolve({cancelled: false, value});
      else audit('ignored ' + request);
    }, failure => operation.reject(failure));
    audit('started ' + request);
    return operation;
  }

  owns(operation) { return this.mounted && this.current === operation; }
  finish(operation) { if (this.current === operation) this.current = null; }
  cancel() {
    const cancelled = this.current;
    this.current = null;
    if (cancelled) cancelled.resolve({cancelled: true, value: ''});
  }
}

function TransitionCancelWorker({instance, transport, onAudit}) {
  const [draft, setDraft] = useState('');
  const [result, setResult] = useState('none');
  const [ready, setReady] = useState(false);
  const owner = useRef(new Owner());
  const replacement = useRef(null);
  const [pending, start] = useTransition();
  useEffect(() => {
    owner.current.activate();
    setReady(true);
    return () => owner.current.dispose();
  }, []);
  const observe = operation => {
    const request = operation.request;
    start(async () => {
      const outcome = await operation.consumer;
      onAudit('consumer ' + request + (outcome.cancelled ? ' cancelled' : ' completed'));
      if (!outcome.cancelled && owner.current.owns(operation)) {
        startTransition(() => setResult(outcome.value));
        onAudit('published ' + request + ' instance ' + instance);
        owner.current.finish(operation);
      }
    });
  };
  const startRequest = request => {
    const operation = owner.current.begin(request, transport, onAudit);
    observe(operation);
    return operation;
  };
  return <section data-testid="transition-worker" aria-label="Transition worker">
    <p data-testid="worker-instance">{instance}</p>
    <p data-testid="worker-ready">{ready ? 'ready' : 'preparing'}</p>
    <label htmlFor="urgent-draft">Urgent draft</label>
    <input id="urgent-draft" value={draft} onChange={event => setDraft(event.target.value)} />
    <p data-testid="urgent-value">{draft}</p>
    <p data-testid="transition-pending">{pending ? 'pending' : 'idle'}</p>
    <p data-testid="transition-result">{result}</p>
    {requests.filter(request => request !== 'RACE_OLD' && request !== 'RACE_NEW')
      .map(request => <button key={request} disabled={!ready || transport.started(request)}
        onClick={() => startRequest(request)}>
        Start {request}
      </button>)}
    <button disabled={!ready || transport.started('RACE_OLD')} onClick={() => {
      const original = startRequest('RACE_OLD');
      transport.afterCompletion('RACE_OLD', () => {
        if (owner.current.owns(original)) {
          replacement.current = owner.current.begin('RACE_NEW', transport, onAudit);
        }
      });
    }}>Start completion race</button>
    <button disabled={replacement.current === null} onClick={() => {
      const operation = replacement.current;
      replacement.current = null;
      observe(operation);
    }}>Await replacement</button>
    <button disabled={!ready} onClick={() => owner.current.cancel()}>Cancel current</button>
  </section>;
}

export function CombinedTransitionCancel() {
  const [mounted, setMounted] = useState(true);
  const [instance, setInstance] = useState(1);
  const [audit, setAudit] = useState([]);
  const transport = useRef(new Transport());
  const append = line => setAudit(previous => {
    if (previous.length >= 64) throw new Error('Fixture audit exceeded its bounded scenario');
    return [...previous, line];
  });
  return <main>
    <button disabled={!mounted} onClick={() => setMounted(false)}>Unmount worker</button>
    <button disabled={mounted} onClick={() => {
      setInstance(value => value + 1);
      setMounted(true);
    }}>Mount worker</button>
    <section aria-label="Transport controls">
      {requests.map(request => <div key={request}>
        <button disabled={!transport.current.pending(request)} onClick={() => {
          transport.current.complete(request);
          append('released ' + request);
        }}>Resolve {request}</button>
        <p data-testid={'transport-' + request}>{transport.current.status(request)}</p>
      </div>)}
    </section>
    <pre data-testid="transition-audit">{audit.join('\n')}</pre>
    {mounted ? <TransitionCancelWorker key={instance} instance={instance} transport={transport.current} onAudit={append} /> : null}
  </main>;
}
