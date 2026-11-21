import { isValidElement } from 'react';

export function IsValidElement() {
  const checks = [<p />, <></>, 'text', 7, null].map(value => String(isValidElement(value)));
  return <main><p role="status">{checks.join(' ')}</p></main>;
}
