import { Children } from 'react';

function Inspect({ children }) {
  return (
    <section aria-label="Inspect">
      <p role="status">{Children.count(children)}/{Children.toArray(children).length}</p>
      <ul>{Children.map(children, child => <li>{child}</li>)}</ul>
    </section>
  );
}

function Single({ children }) {
  return <strong data-testid="only">{Children.only(children)}</strong>;
}

export function ChildrenUtilities() {
  return (
    <main>
      <Inspect>
        <span>a</span>
        {['b', 'c'].map(name => <span key={name}>{name}</span>)}
      </Inspect>
      <Single><em>one</em></Single>
    </main>
  );
}
