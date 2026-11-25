import {Component, useActionState, useEffect, useState} from 'react';
import {useFormStatus} from 'react-dom';
import {log} from '../bench.js';

class ActionQueueControl {
  constructor(generation) {
    this.generation = generation;
    this.started = 0;
    this.mount = null;
  }

  activate() {
    if (this.mount !== null && !this.mount.disposed) throw new Error('Action owner is already mounted');
    this.mount = {disposed: false, pending: null};
  }

  dispose() {
    if (this.mount === null || this.mount.disposed) return;
    this.mount.disposed = true;
    if (this.mount.pending !== null) this.takePending().resolve();
  }

  async submit(previous, data) {
    const owner = this.mount;
    if (owner === null || owner.disposed) {
      log('action ignored ' + this.generation);
      return previous;
    }
    if (owner.pending !== null) throw new Error('React started concurrent queued actions');
    const text = data.get('step');
    const step = Number(text);
    const call = ++this.started;
    log('queue start ' + this.generation + ':' + call + ' previous=' + previous + ' step=' + text);
    await new Promise((resolve, reject) => { owner.pending = {resolve, reject}; });
    if (owner.disposed) {
      log('action cancelled ' + this.generation + ':' + call);
      return previous;
    }
    const next = previous + step;
    log('queue finish ' + this.generation + ':' + call + ' total=' + next);
    return next;
  }

  takePending() {
    if (this.mount === null || this.mount.pending === null) throw new Error('No queued action is waiting');
    const pending = this.mount.pending;
    this.mount.pending = null;
    return pending;
  }

  release() { this.takePending().resolve(); }
  fail() { this.takePending().reject(new Error('queued action failed')); }
}

class Boundary extends Component {
  state = {error: null};
  static getDerivedStateFromError(error) { return {error}; }
  render() {
    return this.state.error
      ? <p role="alert">{'failed: ' + this.state.error.message}</p>
      : this.props.children;
  }
}

function QueuedActionStatus() {
  const status = useFormStatus();
  const text = status.pending ? 'pending ' + status.method + ' ' + status.data.get('step') : 'idle';
  return <p data-testid="queue-form-status">{text}</p>;
}

function QueuedActionForm({control}) {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    control.activate();
    setReady(true);
    return () => control.dispose();
  }, [control]);
  const [total, action, pending] = useActionState((previous, data) => control.submit(previous, data), 0);
  return <form action={action}>
    <label>Step<input name="step" defaultValue="2" /></label>
    <button type="submit" disabled={!ready}>Queue step</button>
    <QueuedActionStatus />
    <p data-testid="queue-pending">{pending ? 'pending' : 'idle'}</p>
    <p data-testid="queue-total">{total}</p>
  </form>;
}

export function CombinedActionQueue() {
  const [session, setSession] = useState(() => ({generation: 0, control: new ActionQueueControl(0)}));
  const [mounted, setMounted] = useState(true);
  return <main>
    <h1>Combined action queue</h1>
    <p data-testid="queue-generation">{session.generation}</p>
    <button disabled={!mounted} onClick={() => {
      session.control.dispose();
      const generation = session.generation + 1;
      setSession({generation, control: new ActionQueueControl(generation)});
    }}>Retry form</button>
    <button disabled={!mounted} onClick={() => session.control.release()}>Release action</button>
    <button disabled={!mounted} onClick={() => session.control.fail()}>Fail action</button>
    <button disabled={!mounted} onClick={() => setMounted(false)}>Unmount form</button>
    <button disabled={mounted} onClick={() => {
      const generation = session.generation + 1;
      setSession({generation, control: new ActionQueueControl(generation)});
      setMounted(true);
    }}>Mount form</button>
    <p data-testid="queue-sibling">sibling survives</p>
    {mounted ? <section key={String(session.generation)} data-testid="queue-owner">
      <Boundary><QueuedActionForm control={session.control} /></Boundary>
    </section> : null}
  </main>;
}
