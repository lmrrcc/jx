import {useLayoutEffect} from 'react';
import {cases} from './cases.jsx';
import {caseId} from './bench.js';

export function route() {
  const id = caseId();
  const Case = Object.hasOwn(cases, id) ? cases[id] : null;
  if (!Case) throw new Error('Unknown route case: ' + id);
  return function CommittedBenchRoute() {
    useLayoutEffect(() => {
      globalThis.__benchLazyReady = true;
      return () => { globalThis.__benchLazyReady = false; };
    }, []);
    return <Case />;
  };
}
