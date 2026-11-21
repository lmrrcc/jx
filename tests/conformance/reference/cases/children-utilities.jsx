import { Children } from 'react';

function Inspect({ children }) {
  return (
    <section aria-label="Inspect">
      <p role="status">{Children.count(children)}/{Children.toArray(children).length}</p>
      <ul>{Children.map(children, child => <li>{child}</li>)}</ul>
    </section>
  );
}

export function ChildrenUtilities() {
  return (
    <main>
      <Inspect>
        <span>a</span>
        {null}
        {['b', 'c'].map(name => <span key={name}>{name}</span>)}
      </Inspect>
    </main>
  );
}
