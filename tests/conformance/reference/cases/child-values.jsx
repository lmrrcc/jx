export function ChildValues() {
  const missing = null;
  const flag = true;
  return (
    <main>
      <p data-testid="empty">{missing}{flag}{!flag}</p>
      <p data-testid="scalars">{42}{'x'}{1.5}</p>
      <p data-testid="nested">{['a', ['b', 'c']]}{['d', 'e']}</p>
    </main>
  );
}
