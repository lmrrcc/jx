import {startTransition, useActionState, useEffect, useMemo, useRef, useState} from 'react';
import {useFormStatus} from 'react-dom';

function Submit({primary, alternate}) {
  const status = useFormStatus();
  const nativeAction = useFormStatus().action;
  const observed = useRef(null);
  const stable = !status.pending || observed.current === null || observed.current.action !== status.action
    || observed.current.nativeAction === nativeAction;
  useEffect(() => {
    if (status.pending) observed.current = {action: status.action, nativeAction};
  }, [status.pending, status.action]);
  const identity = typeof status.action === 'string' ? 'url:' + status.action : status.action === null ? 'none' : status.action === primary ? 'primary'
    : status.action === alternate ? 'alternate' : 'unknown';
  const details = status.pending ? status.method + ':' + status.data.get('payload') : 'idle';
  return <section>
    <button type="submit" disabled={status.pending}>{status.pending ? 'Sending' : 'Send'}</button>
    <button type="submit" formAction={alternate} disabled={status.pending}>Alternate</button>
    <p data-testid="form-action-status">{identity + ':' + details + ':' + stable}</p>
  </section>;
}

function NativeSubmit() {
  const status = useFormStatus();
  const observed = useRef(null);
  const stable = status.action === null || observed.current === null || observed.current === status.action;
  useEffect(() => {
    if (observed.current === null && status.action !== null) observed.current = status.action;
  }, [status.action]);
  return <section>
    <button type="submit" disabled={status.pending}>Native send</button>
    <button type="submit" formAction={observed.current} disabled={status.pending || observed.current === null}>Native replay</button>
    <p data-testid="native-form-status">{(status.pending ? 'pending:' : 'idle:') + (status.action === null ? 'none:' : 'function:') + stable}</p>
  </section>;
}

function NativeFormStatus({submit}) {
  const action = useMemo(() => data => submit(), [submit]);
  return <form action={action}>
    <input name="payload" defaultValue="native" />
    <NativeSubmit />
  </form>;
}

export function FormStatus() {
  const pending = useMemo(() => ({resolver: null,
    submit() {
      if (this.resolver !== null) throw new Error('Submission already pending');
      return new Promise(resolve => { this.resolver = resolve; });
    },
    release() {
      const resolve = this.resolver;
      this.resolver = null;
      if (resolve !== null) resolve();
    }
  }), []);
  useEffect(() => () => pending.release(), []);
  const primary = useMemo(() => () => pending.submit(), []);
  const alternate = useMemo(() => () => pending.submit(), []);
  const [result, action] = useActionState(previous => pending.submit().then(() => previous + 1), 0);
  const [dispatcher, setDispatcher] = useState(false);
  const [urlAction, setUrlAction] = useState(false);
  const [nativeMode, setNativeMode] = useState(false);
  const nativeProps = useMemo(() => ({submit: () => pending.submit()}), []);
  const [pulse, setPulse] = useState(0);
  const outside = useFormStatus();
  const selected = dispatcher ? action : primary;
  return <main>
    {nativeMode ? <NativeFormStatus {...nativeProps} /> : <form action={urlAction ? '/submit' : selected} onSubmit={event => {
      if (urlAction) {
        event.preventDefault();
        startTransition(() => pending.submit());
      }
    }}>
      <input aria-label="Payload" name="payload" defaultValue="sample" />
      <Submit primary={selected} alternate={alternate} />
    </form>}
    <button onClick={() => pending.release()}>Release</button>
    <button onClick={() => setPulse(value => value + 1)}>Pulse</button>
    <button onClick={() => setDispatcher(true)}>Use dispatcher</button>
    <button onClick={() => setUrlAction(true)}>Use URL action</button>
    <button onClick={() => setNativeMode(true)}>Use native form</button>
    <p data-testid="form-pulse">{pulse}</p>
    <p data-testid="form-result">{result}</p>
    <p data-testid="form-outside">{outside.action === null && !outside.pending ? 'outside idle' : 'invalid'}</p>
  </main>;
}
