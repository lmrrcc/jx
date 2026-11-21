import { cloneElement } from 'react';

export function CloneElement() {
  const original = <button className="base" title="original">Press</button>;
  return (
    <main>
      {cloneElement(original, { className: 'cloned', 'data-extra': 'yes' }, 'Cloned')}
    </main>
  );
}
