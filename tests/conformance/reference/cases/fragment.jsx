import { useState } from 'react';

export function FragmentCase() {
  const [more, setMore] = useState(false);
  return (
    <main>
      <button onClick={() => setMore(true)}>More</button>
      <ul>
        <><li>one</li><li>two</li></>
        {more ? <><li>three</li><li>four</li></> : null}
      </ul>
    </main>
  );
}
