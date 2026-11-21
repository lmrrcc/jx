import { Suspense, createContext, use } from 'react';

let resolveValue = () => {};
const value = new Promise(resolve => { resolveValue = resolve; });
const Tone = createContext('plain');

function Loaded({ loud }) {
  const text = use(value);
  // Unlike other hooks, use may be called conditionally.
  const tone = loud ? use(Tone) : 'quiet';
  return <p role="status">{text} {tone}</p>;
}

export function UsePromise() {
  return (
    <main>
      <button onClick={() => resolveValue('loaded')}>Resolve</button>
      <Tone value="bright">
        <Suspense fallback={<p role="status">loading</p>}>
          <Loaded loud />
        </Suspense>
      </Tone>
    </main>
  );
}
