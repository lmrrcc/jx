import { useEffect, useEffectEvent, useState } from 'react';
import { log } from '../bench.js';

function Connection({ room, theme }) {
  const onConnected = useEffectEvent(() => log('connected ' + room + ' ' + theme));
  useEffect(() => {
    onConnected();
    return () => log('disconnected ' + room);
  }, [room]);
  return <p role="status">{room} {theme}</p>;
}

export function EffectEvent() {
  const [room, setRoom] = useState('a');
  const [theme, setTheme] = useState('light');
  return (
    <main>
      <Connection room={room} theme={theme} />
      <button onClick={() => setTheme('dark')}>Change theme</button>
      <button onClick={() => setRoom('b')}>Change room</button>
    </main>
  );
}
