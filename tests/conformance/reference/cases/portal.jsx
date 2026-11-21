import { useState } from 'react';
import { createPortal } from 'react-dom';

export function Portal() {
  const [clicks, setClicks] = useState(0);
  return (
    <main>
      <div data-testid="owner" onClick={() => setClicks(previous => previous + 1)}>
        <p role="status">{clicks}</p>
        {createPortal(<button data-testid="ported">Ported</button>, document.body)}
      </div>
    </main>
  );
}
