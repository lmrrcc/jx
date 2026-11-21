import { createContext, useContext, useState } from 'react';

const Theme = createContext('light');

function ThemeLabel({ name }) {
  const theme = useContext(Theme);
  return <p data-theme-of={name}>{theme}</p>;
}

export function ContextNested() {
  const [theme, setTheme] = useState('dark');
  return (
    <main>
      <ThemeLabel name="outside" />
      <Theme value={theme}>
        <ThemeLabel name="inside" />
        <Theme value="fixed"><ThemeLabel name="nested" /></Theme>
      </Theme>
      <button onClick={() => setTheme('contrast')}>Change provider</button>
    </main>
  );
}
