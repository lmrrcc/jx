import {captureOwnerStack, useLayoutEffect, useState, version} from 'react';
import {unstable_batchedUpdates, version as domVersion} from 'react-dom';
import {version as clientVersion} from 'react-dom/client';

function DiagnosticOwner({id}) {
  const [captured, setCaptured] = useState('pending');
  useLayoutEffect(() => {
    setCaptured(typeof captureOwnerStack === 'function' ? captureOwnerStack() : null);
  }, []);
  return <output data-testid={id} title={captured === 'pending' || captured === null ? '' : captured}>
    {captured === 'pending' ? 'pending' : captured === null ? 'absent' : 'present'}
  </output>;
}

function NativeOwner() { return <DiagnosticOwner id="interop-owner-stack" />; }

export function RuntimeUtilities() {
  const [count, setCount] = useState(0);
  const [result, setResult] = useState('ready');
  return <main>
    <output data-testid="react-version">{version}</output>
    <output data-testid="dom-version">{domVersion}</output>
    <output data-testid="client-version">{clientVersion}</output>
    <DiagnosticOwner id="owner-stack" />
    <NativeOwner />
    <p role="status" data-testid="batch-result">{count + ':' + result}</p>
    <button onClick={() => {
      const token = {};
      const returned = unstable_batchedUpdates(() => {
        setCount(value => value + 1);
        setCount(value => value + 1);
        return token;
      });
      const empty = unstable_batchedUpdates(() => null);
      setResult(returned === token && empty === null ? 'same' : 'wrong');
    }}>Batch</button>
    <button onClick={() => {
      try {
        unstable_batchedUpdates(() => { throw new Error('batch failed'); });
        setResult('failure swallowed');
      } catch (error) { setResult(error.message); }
    }}>Fail batch</button>
  </main>;
}
